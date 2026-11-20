package com.ribeira.auth.domain;

public enum UserStatus {
  ACTIVE,
  /** Temporarily prevented from signing in; can be reactivated. */
  BLOCKED,
  /** No longer part of the organization; kept for history and audit. */
  DISABLED
}
