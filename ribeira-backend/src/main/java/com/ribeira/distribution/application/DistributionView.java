package com.ribeira.distribution.application;

import com.ribeira.distribution.domain.Distribution;
import com.ribeira.shared.domain.UnitOfMeasure;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public record DistributionView(
    UUID id,
    UUID communityId,
    Instant distributedAt,
    String notes,
    UUID performedBy,
    Set<UUID> familyIds,
    List<ItemView> items) {

  public record ItemView(
      UUID id, UUID batchId, UUID foodId, BigDecimal quantity, UnitOfMeasure unit) {}

  static DistributionView of(Distribution d) {
    return new DistributionView(
        d.getId(),
        d.getCommunityId(),
        d.getDistributedAt(),
        d.getNotes(),
        d.getPerformedBy(),
        d.getFamilyIds(),
        d.getItems().stream()
            .map(
                i ->
                    new ItemView(
                        i.getId(), i.getBatchId(), i.getFoodId(), i.getQuantity(), i.getUnit()))
            .toList());
  }
}
