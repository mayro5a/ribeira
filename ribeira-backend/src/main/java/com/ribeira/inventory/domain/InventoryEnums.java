package com.ribeira.inventory.domain;

/** Small vocabularies of the inventory domain, kept together for readability. */
public final class InventoryEnums {

  private InventoryEnums() {}

  /** Distinguishes "no expiry" from "expiry not informed" (RF05). */
  public enum ExpirationStatus {
    KNOWN,
    NOT_APPLICABLE,
    UNKNOWN
  }

  public enum BatchStatus {
    AVAILABLE,
    /** Held for inspection; can still register losses or adjustments, not distributions. */
    BLOCKED,
    DEPLETED
  }

  public enum MovementType {
    ENTRY,
    DISTRIBUTION,
    LOSS,
    ADJUSTMENT
  }

  /** What originated a movement; together with the source ID it makes every change traceable. */
  public enum MovementSource {
    DONATION,
    MANUAL_ENTRY,
    DISTRIBUTION,
    LOSS,
    ADJUSTMENT
  }
}
