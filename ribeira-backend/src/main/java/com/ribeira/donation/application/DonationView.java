package com.ribeira.donation.application;

import com.ribeira.donation.domain.Donation;
import com.ribeira.shared.domain.UnitOfMeasure;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record DonationView(
    UUID id,
    UUID communityId,
    UUID donorId,
    String originDescription,
    Instant receivedAt,
    String notes,
    UUID registeredBy,
    List<ItemView> items) {

  public record ItemView(
      UUID id, UUID foodId, BigDecimal quantity, UnitOfMeasure unit, String notes) {}

  static DonationView of(Donation d) {
    return new DonationView(
        d.getId(),
        d.getCommunityId(),
        d.getDonorId(),
        d.getOriginDescription(),
        d.getReceivedAt(),
        d.getNotes(),
        d.getRegisteredBy(),
        d.getItems().stream()
            .map(
                i ->
                    new ItemView(
                        i.getId(), i.getFoodId(), i.getQuantity(), i.getUnit(), i.getNotes()))
            .toList());
  }
}
