package com.ribeira.shared.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AccessGrantsTest {

  private final UUID communityA = UUID.randomUUID();
  private final UUID communityB = UUID.randomUUID();

  @Test
  void communityRoleGrantsOnlyThatCommunity() {
    AccessGrants grants =
        new AccessGrants(
            UUID.randomUUID(),
            UUID.randomUUID(),
            true,
            Set.of(),
            Map.of(communityA, Set.of(Permission.DISTRIBUTION_WRITE)));

    assertThat(grants.permissionsIn(communityA)).containsExactly(Permission.DISTRIBUTION_WRITE);
    assertThat(grants.permissionsIn(communityB)).isEmpty();
  }

  @Test
  void organizationRoleAppliesToEveryCommunityAndCombinesWithCommunityRole() {
    AccessGrants grants =
        new AccessGrants(
            UUID.randomUUID(),
            UUID.randomUUID(),
            true,
            Set.of(Permission.REPORT_READ),
            Map.of(communityA, Set.of(Permission.STOCK_WRITE)));

    assertThat(grants.permissionsIn(communityA))
        .containsExactlyInAnyOrder(Permission.REPORT_READ, Permission.STOCK_WRITE);
    assertThat(grants.permissionsIn(communityB)).containsExactly(Permission.REPORT_READ);
  }

  @Test
  void organizationScopedCatalogsAcceptPermissionFromAnyCommunity() {
    AccessGrants grants =
        new AccessGrants(
            UUID.randomUUID(),
            UUID.randomUUID(),
            true,
            Set.of(),
            Map.of(communityA, Set.of(Permission.DONOR_MANAGE)));

    assertThat(grants.hasAnywhere(Permission.DONOR_MANAGE)).isTrue();
    assertThat(grants.hasAnywhere(Permission.FOOD_MANAGE)).isFalse();
  }
}
