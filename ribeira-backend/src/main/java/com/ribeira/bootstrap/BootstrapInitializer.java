package com.ribeira.bootstrap;

import com.ribeira.auth.application.UserService;
import com.ribeira.organization.application.OrganizationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/** Creates the first organization and its administrator when the database is empty. */
@Component
@Order(1)
class BootstrapInitializer implements ApplicationRunner {

  private static final Logger log = LoggerFactory.getLogger(BootstrapInitializer.class);

  private final BootstrapProperties properties;
  private final OrganizationService organizations;
  private final UserService users;
  private final TransactionTemplate transaction;

  BootstrapInitializer(
      BootstrapProperties properties,
      OrganizationService organizations,
      UserService users,
      TransactionTemplate transaction) {
    this.properties = properties;
    this.organizations = organizations;
    this.users = users;
    this.transaction = transaction;
  }

  @Override
  public void run(ApplicationArguments args) {
    if (!properties.isConfigured() || organizations.anyExists()) {
      return;
    }
    transaction.executeWithoutResult(
        status -> {
          var organizationId = organizations.createOrganization(properties.organizationName());
          users.createInitialAdministrator(
              organizationId,
              properties.adminEmail(),
              properties.adminName(),
              properties.adminPassword());
        });
    log.info("Bootstrap organization and administrator created");
  }
}
