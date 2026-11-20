package com.ribeira.distribution.domain;

import com.ribeira.shared.domain.AssignedIdEntity;
import com.ribeira.shared.domain.UnitOfMeasure;
import jakarta.persistence.CascadeType;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Food handed to families. Immutable once registered; mistakes are corrected by adjustments. */
@Entity
@Table(name = "distributions")
public class Distribution extends AssignedIdEntity {

  @Column(name = "organization_id", nullable = false, updatable = false)
  private UUID organizationId;

  @Column(name = "community_id", nullable = false, updatable = false)
  private UUID communityId;

  @Column(name = "distributed_at", nullable = false, updatable = false)
  private Instant distributedAt;

  @Column(length = 1000, updatable = false)
  private String notes;

  @Column(name = "performed_by", nullable = false, updatable = false)
  private UUID performedBy;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @OneToMany(mappedBy = "distribution", cascade = CascadeType.PERSIST)
  @OrderBy("position")
  private List<DistributionItem> items = new ArrayList<>();

  @ElementCollection
  @CollectionTable(
      name = "distribution_families",
      joinColumns = @JoinColumn(name = "distribution_id"))
  @Column(name = "family_id")
  private Set<UUID> familyIds = new HashSet<>();

  protected Distribution() {}

  public Distribution(
      UUID id,
      UUID organizationId,
      UUID communityId,
      Instant distributedAt,
      String notes,
      UUID performedBy,
      Set<UUID> families,
      Instant createdAt) {
    super(id);
    this.organizationId = organizationId;
    this.communityId = communityId;
    this.distributedAt = distributedAt;
    this.notes = notes;
    this.performedBy = performedBy;
    this.familyIds.addAll(families);
    this.createdAt = createdAt;
  }

  public void addItem(
      UUID itemId,
      UUID batchId,
      UUID foodId,
      BigDecimal quantity,
      UnitOfMeasure unit,
      UUID movementId) {
    items.add(
        new DistributionItem(
            itemId, this, batchId, foodId, quantity, unit, movementId, items.size()));
  }

  public UUID getOrganizationId() {
    return organizationId;
  }

  public UUID getCommunityId() {
    return communityId;
  }

  public Instant getDistributedAt() {
    return distributedAt;
  }

  public String getNotes() {
    return notes;
  }

  public UUID getPerformedBy() {
    return performedBy;
  }

  public List<DistributionItem> getItems() {
    return List.copyOf(items);
  }

  public Set<UUID> getFamilyIds() {
    return Set.copyOf(familyIds);
  }
}
