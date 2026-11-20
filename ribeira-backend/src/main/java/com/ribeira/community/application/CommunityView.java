package com.ribeira.community.application;

import com.ribeira.community.domain.Community;
import java.util.UUID;

public record CommunityView(
    UUID id,
    UUID organizationId,
    String name,
    String description,
    String municipality,
    String approximateLocation,
    UUID primaryDeviceId,
    boolean active) {

  static CommunityView of(Community c) {
    return new CommunityView(
        c.getId(),
        c.getOrganizationId(),
        c.getName(),
        c.getDescription(),
        c.getMunicipality(),
        c.getApproximateLocation(),
        c.getPrimaryDeviceId(),
        c.isActive());
  }
}
