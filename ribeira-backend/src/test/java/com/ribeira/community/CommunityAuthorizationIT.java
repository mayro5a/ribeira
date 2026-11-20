package com.ribeira.community;

import static org.assertj.core.api.Assertions.assertThat;

import com.ribeira.support.ApiIntegrationTest;
import com.ribeira.support.Fixtures;
import com.ribeira.support.Fixtures.TestUser;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Tenant and community isolation (business rule RN07, acceptance scenario 6). */
class CommunityAuthorizationIT extends ApiIntegrationTest {

  private UUID organization;
  private UUID communityA;
  private UUID communityB;
  private UUID otherTenantCommunity;
  private TestUser distributorOfA;
  private TestUser admin;

  @BeforeEach
  void setUp() {
    organization = fixtures.organization();
    communityA = fixtures.community(organization);
    communityB = fixtures.community(organization);
    otherTenantCommunity = fixtures.community(fixtures.organization());
    distributorOfA = fixtures.member(organization, communityA, Fixtures.DISTRIBUTOR);
    admin = fixtures.admin(organization);
  }

  @Test
  void memberOnlySeesAuthorizedCommunities() {
    var response = api.get("/api/v1/communities", api.login(distributorOfA));

    assertThat(response.status()).isEqualTo(200);
    assertThat(response.body().findValuesAsText("id")).containsExactly(communityA.toString());
  }

  @Test
  void accessToUnauthorizedCommunityOfSameOrganizationIsDenied() {
    var response = api.get("/api/v1/communities/" + communityB, api.login(distributorOfA));

    assertThat(response.status()).isEqualTo(403);
    assertThat(response.code()).isEqualTo("ACCESS_DENIED");
    assertThat(response.body().toString()).doesNotContain(communityB.toString() + "\",\"name");
  }

  @Test
  void communityOfAnotherOrganizationLooksNonexistent() {
    String token = api.login(admin);

    var response = api.get("/api/v1/communities/" + otherTenantCommunity, token);

    assertThat(response.status()).isEqualTo(404);
    assertThat(response.body().path("name").isMissingNode()).isTrue();
  }

  @Test
  void organizationAdminSeesAndManagesAllCommunities() {
    String token = api.login(admin);

    assertThat(api.get("/api/v1/communities", token).body().findValuesAsText("id"))
        .containsExactlyInAnyOrder(communityA.toString(), communityB.toString());

    var created = api.post("/api/v1/communities", token, Map.of("name", "Nova comunidade X"));
    assertThat(created.status()).isEqualTo(201);

    var duplicated = api.post("/api/v1/communities", token, Map.of("name", "nova comunidade x"));
    assertThat(duplicated.status()).isEqualTo(409);
    assertThat(duplicated.code()).isEqualTo("COMMUNITY_NAME_IN_USE");
  }

  @Test
  void communityManagementRequiresPermission() {
    var response =
        api.post("/api/v1/communities", api.login(distributorOfA), Map.of("name", "Indevida"));

    assertThat(response.status()).isEqualTo(403);
  }

  @Test
  void anotherOrganizationIsNotVisible() {
    UUID otherOrganization = fixtures.organization();

    var response = api.get("/api/v1/organizations/" + otherOrganization, api.login(admin));

    assertThat(response.status()).isEqualTo(404);
  }

  @Test
  void adminCreatesUserWhoCanLogInAndBlockingTakesEffectImmediately() {
    String adminToken = api.login(admin);
    String email = "novo-" + UUID.randomUUID() + "@teste.ribeira.local";
    var created =
        api.post(
            "/api/v1/users",
            adminToken,
            Map.of(
                "email",
                email,
                "displayName",
                "Pessoa Fictícia",
                "password",
                "senha-segura-123",
                "communityRoles",
                Map.of(communityA.toString(), Fixtures.VIEWER.toString())));
    assertThat(created.status()).isEqualTo(201);
    assertThat(created.body().has("passwordHash")).isFalse();
    String userId = created.body().path("id").asText();
    TestUser newUser = new TestUser(UUID.fromString(userId), organization, email);

    // ApiClient logs in with the fixture password; reset it to that first.
    assertThat(
            api.post(
                    "/api/v1/users/" + userId + "/password",
                    adminToken,
                    Map.of("password", Fixtures.PASSWORD))
                .status())
        .isEqualTo(204);
    String userToken = api.login(newUser);
    assertThat(api.get("/api/v1/communities", userToken).status()).isEqualTo(200);

    var blocked =
        api.put(
            "/api/v1/users/" + userId,
            adminToken,
            Map.of("displayName", "Pessoa Fictícia", "status", "BLOCKED"));
    assertThat(blocked.status()).isEqualTo(200);

    var afterBlock = api.get("/api/v1/communities", userToken);
    assertThat(afterBlock.status()).isEqualTo(403);
    assertThat(afterBlock.code()).isEqualTo("USER_INACTIVE");
  }

  @Test
  void cannotGrantAccessToCommunityOfAnotherOrganization() {
    var response =
        api.post(
            "/api/v1/users",
            api.login(admin),
            Map.of(
                "email",
                "x-" + UUID.randomUUID() + "@teste.ribeira.local",
                "displayName",
                "Pessoa",
                "password",
                "senha-segura-123",
                "communityRoles",
                Map.of(otherTenantCommunity.toString(), Fixtures.VIEWER.toString())));

    assertThat(response.status()).isEqualTo(404);
  }

  @Test
  void organizationCanCreateCustomRole() {
    String token = api.login(admin);
    var role =
        api.post(
            "/api/v1/roles",
            token,
            Map.of(
                "code",
                "LOCAL_AUDITOR",
                "name",
                "Auditor local",
                "permissions",
                List.of("COMMUNITY_READ", "REPORT_READ")));
    assertThat(role.status()).isEqualTo(201);

    var roles = api.get("/api/v1/roles", token);
    assertThat(roles.body().findValuesAsText("code")).contains("ORG_ADMIN", "LOCAL_AUDITOR");

    var systemEdit =
        api.put(
            "/api/v1/roles/" + Fixtures.VIEWER,
            token,
            Map.of("name", "x", "permissions", List.of("COMMUNITY_READ")));
    assertThat(systemEdit.code()).isEqualTo("SYSTEM_ROLE_IMMUTABLE");
  }

  @Test
  void userCannotBlockThemselves() {
    var response =
        api.put(
            "/api/v1/users/" + admin.id(),
            api.login(admin),
            Map.of("displayName", "Admin", "status", "DISABLED"));

    assertThat(response.code()).isEqualTo("SELF_LOCKOUT");
  }
}
