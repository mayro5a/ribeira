package com.ribeira.bootstrap;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * First organization and administrator, created once on an empty database. All values come from
 * environment variables; nothing is created when they are absent.
 */
@ConfigurationProperties("ribeira.bootstrap")
public record BootstrapProperties(
    String organizationName, String adminEmail, String adminName, String adminPassword) {

  boolean isConfigured() {
    return notBlank(organizationName)
        && notBlank(adminEmail)
        && notBlank(adminName)
        && notBlank(adminPassword);
  }

  private static boolean notBlank(String value) {
    return value != null && !value.isBlank();
  }
}
