package com.ribeira.shared.security;

/**
 * Fine-grained actions. Roles are configurable sets of these (see docs/security.md). Permissions
 * marked as organization-scoped are evaluated against organization roles and any community role.
 */
public enum Permission {
  ORGANIZATION_MANAGE,
  COMMUNITY_MANAGE,
  USER_MANAGE,
  DEVICE_MANAGE,
  COMMUNITY_READ,
  FAMILY_READ,
  FAMILY_WRITE,
  DONOR_MANAGE,
  FOOD_MANAGE,
  DONATION_WRITE,
  STOCK_READ,
  STOCK_WRITE,
  STOCK_ADJUST,
  DISTRIBUTION_WRITE,
  LOSS_WRITE,
  MONITORING_WRITE,
  ALERT_RESOLVE,
  REPORT_READ,
  SYNC_CONFLICT_RESOLVE
}
