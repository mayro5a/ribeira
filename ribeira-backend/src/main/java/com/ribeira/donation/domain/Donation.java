package com.ribeira.donation.domain;

import com.ribeira.shared.domain.AssignedIdEntity;
import com.ribeira.shared.domain.UnitOfMeasure;
import com.ribeira.shared.exception.BusinessRuleException;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** A receipt of food in a community. Immutable after registration: corrections are adjustments. */
@Entity
@Table(name = "donations")
public class Donation extends AssignedIdEntity {

  @Column(name = "organization_id", nullable = false, updatable = false)
  private UUID organizationId;

  @Column(name = "community_id", nullable = false, updatable = false)
  private UUID communityId;

  @Column(name = "donor_id", updatable = false)
  private UUID donorId;

  @Column(name = "origin_description", length = 200, updatable = false)
  private String originDescription;

  @Column(name = "received_at", nullable = false, updatable = false)
  private Instant receivedAt;

  @Column(length = 1000, updatable = false)
  private String notes;

  @Column(name = "registered_by", nullable = false, updatable = false)
  private UUID registeredBy;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @OneToMany(mappedBy = "donation", cascade = CascadeType.PERSIST)
  @OrderBy("position")
  private List<DonationItem> items = new ArrayList<>();

  protected Donation() {}

  public Donation(
      UUID id,
      UUID organizationId,
      UUID communityId,
      UUID donorId,
      String originDescription,
      Instant receivedAt,
      String notes,
      UUID registeredBy,
      Instant createdAt) {
    super(id);
    boolean hasOrigin = originDescription != null && !originDescription.isBlank();
    if (donorId == null && !hasOrigin) {
      // RN03: every entry must have an identifiable origin.
      throw new BusinessRuleException("ORIGIN_REQUIRED", "Informe o doador ou a origem da doação.");
    }
    this.organizationId = organizationId;
    this.communityId = communityId;
    this.donorId = donorId;
    this.originDescription = hasOrigin ? originDescription.trim() : null;
    this.receivedAt = receivedAt;
    this.notes = notes;
    this.registeredBy = registeredBy;
    this.createdAt = createdAt;
  }

  public DonationItem addItem(
      UUID itemId, UUID foodId, BigDecimal quantity, UnitOfMeasure unit, String itemNotes) {
    DonationItem item =
        new DonationItem(
            itemId, this, foodId, unit.requirePositive(quantity), unit, itemNotes, items.size());
    items.add(item);
    return item;
  }

  public UUID getOrganizationId() {
    return organizationId;
  }

  public UUID getCommunityId() {
    return communityId;
  }

  public UUID getDonorId() {
    return donorId;
  }

  public String getOriginDescription() {
    return originDescription;
  }

  public Instant getReceivedAt() {
    return receivedAt;
  }

  public String getNotes() {
    return notes;
  }

  public UUID getRegisteredBy() {
    return registeredBy;
  }

  public List<DonationItem> getItems() {
    return List.copyOf(items);
  }
}
