package com.ribeira.distribution.application;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** DISTRIBUTION_CREATE, shared by REST and synchronization. */
public record CreateDistribution(
    @NotNull UUID communityId,
    @NotNull UUID distributionId,
    @NotNull Instant distributedAt,
    @Size(max = 1000) String notes,
    @NotEmpty @Size(max = 200) Set<@NotNull UUID> familyIds,
    @NotEmpty @Size(max = 100) List<@Valid @NotNull Item> items) {

  public record Item(
      @NotNull UUID itemId, @NotNull UUID batchId, @NotNull @Positive BigDecimal quantity) {}
}
