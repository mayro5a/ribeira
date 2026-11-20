package com.ribeira.shared.security;

import com.ribeira.shared.exception.ForbiddenException;
import com.ribeira.shared.exception.ResourceNotFoundException;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

/**
 * Central authorization for application services. Never trust a community ID sent by the client:
 * every use case calls one of the {@code require*} methods with the resource's real community.
 *
 * <p>A community from another organization is reported as "not found" so that IDs of other tenants
 * cannot be probed.
 */
@Component
public class AccessControl {

  public static final String ORGANIZATION_CLAIM = "org";
  private static final String GRANTS_ATTRIBUTE = AccessControl.class.getName() + ".grants";

  private final AccessGrantsProvider grantsProvider;
  private final CommunityDirectory communities;

  public AccessControl(AccessGrantsProvider grantsProvider, CommunityDirectory communities) {
    this.grantsProvider = grantsProvider;
    this.communities = communities;
  }

  public Actor actor() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication != null && authentication.getPrincipal() instanceof Jwt jwt) {
      return new Actor(
          UUID.fromString(jwt.getSubject()),
          UUID.fromString(jwt.getClaimAsString(ORGANIZATION_CLAIM)));
    }
    throw new AuthenticationCredentialsNotFoundException("No authenticated user");
  }

  public AccessGrants grants() {
    Actor actor = actor();
    RequestAttributes request = RequestContextHolder.getRequestAttributes();
    if (request != null
        && request.getAttribute(GRANTS_ATTRIBUTE, RequestAttributes.SCOPE_REQUEST)
            instanceof AccessGrants cached
        && cached.userId().equals(actor.userId())) {
      return cached;
    }
    AccessGrants grants =
        grantsProvider
            .load(actor.userId())
            .filter(AccessGrants::active)
            .filter(g -> g.organizationId().equals(actor.organizationId()))
            .orElseThrow(
                () -> new ForbiddenException("USER_INACTIVE", "Usuário bloqueado ou desativado."));
    if (request != null) {
      request.setAttribute(GRANTS_ATTRIBUTE, grants, RequestAttributes.SCOPE_REQUEST);
    }
    return grants;
  }

  /** For organization-level actions (manage users, communities, catalogs). */
  public Actor requireOrganization(Permission permission) {
    AccessGrants grants = grants();
    if (!grants.organizationPermissions().contains(permission)) {
      throw denied();
    }
    return actor();
  }

  /** For organization-scoped catalogs usable from any authorized community (foods, donors). */
  public Actor requireAnywhere(Permission permission) {
    if (!grants().hasAnywhere(permission)) {
      throw denied();
    }
    return actor();
  }

  public Actor requireCommunity(UUID communityId, Permission permission) {
    if (!can(communityId, permission)) {
      requireCommunityVisible(communityId);
      throw denied();
    }
    return actor();
  }

  public boolean can(UUID communityId, Permission permission) {
    Actor actor = actor();
    boolean sameOrganization =
        communities
            .organizationOf(communityId)
            .map(org -> org.equals(actor.organizationId()))
            .orElse(false);
    return sameOrganization && grants().permissionsIn(communityId).contains(permission);
  }

  /** Communities of the actor's organization where the actor holds the permission. */
  public Set<UUID> communitiesWith(Permission permission) {
    AccessGrants grants = grants();
    List<UUID> candidates =
        grants.organizationPermissions().contains(permission)
            ? communities.communityIdsOf(grants.organizationId())
            : List.copyOf(grants.communityPermissions().keySet());
    return candidates.stream()
        .filter(id -> grants.permissionsIn(id).contains(permission))
        .collect(java.util.stream.Collectors.toUnmodifiableSet());
  }

  /** Other tenants' communities are indistinguishable from nonexistent ones (404). */
  private void requireCommunityVisible(UUID communityId) {
    Actor actor = actor();
    boolean sameOrganization =
        communities
            .organizationOf(communityId)
            .map(org -> org.equals(actor.organizationId()))
            .orElse(false);
    if (!sameOrganization) {
      throw new ResourceNotFoundException("Comunidade", communityId);
    }
  }

  private static ForbiddenException denied() {
    return new ForbiddenException("Você não tem permissão para esta ação.");
  }
}
