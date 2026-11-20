package com.ribeira.bootstrap;

import com.ribeira.auth.application.UserService;
import com.ribeira.auth.application.UserService.NewUser;
import com.ribeira.community.application.CommunityService;
import com.ribeira.community.domain.Community.CommunityDetails;
import com.ribeira.donation.application.DonationService;
import com.ribeira.donation.application.ReceiveDonation;
import com.ribeira.donor.application.DonorService;
import com.ribeira.donor.domain.Donor.DonorType;
import com.ribeira.family.application.FamilyService;
import com.ribeira.family.domain.Family.FamilyDetails;
import com.ribeira.family.domain.Family.FamilyStatus;
import com.ribeira.food.application.FoodService;
import com.ribeira.food.application.FoodView;
import com.ribeira.food.domain.Food.FoodDetails;
import com.ribeira.food.domain.FoodCategory;
import com.ribeira.inventory.application.InventoryViews.StorageLocationView;
import com.ribeira.inventory.application.StorageLocationService;
import com.ribeira.inventory.domain.InventoryEnums.ExpirationStatus;
import com.ribeira.monitoring.application.MonitoringService;
import com.ribeira.monitoring.domain.StorageCondition.ConditionSpec;
import com.ribeira.organization.application.OrganizationService;
import com.ribeira.shared.domain.UnitOfMeasure;
import com.ribeira.shared.security.AccessControl;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

/**
 * Fictitious demonstration data (no real people, places or organizations). Runs once, only when
 * enabled and never in the prod profile. Goes through the application services, so every rule,
 * audit entry and synchronization feed entry is produced exactly as in real use.
 */
@Component
@Order(2)
@ConditionalOnProperty(name = "ribeira.demo.enabled", havingValue = "true")
class DemoDataInitializer implements ApplicationRunner {

  static final String ORGANIZATION_NAME = "Organização Demonstração Ribeira";
  static final String EMAIL_DOMAIN = "@demo.ribeira.local";
  private static final UUID COORDINATOR = UUID.fromString("00000000-0000-0000-0000-000000000102");
  private static final UUID STOCK_KEEPER = UUID.fromString("00000000-0000-0000-0000-000000000103");
  private static final UUID DISTRIBUTOR = UUID.fromString("00000000-0000-0000-0000-000000000104");
  private static final UUID VIEWER = UUID.fromString("00000000-0000-0000-0000-000000000105");
  private static final Logger log = LoggerFactory.getLogger(DemoDataInitializer.class);

  private final DemoProperties properties;
  private final Environment environment;
  private final OrganizationService organizations;
  private final UserService users;
  private final CommunityService communities;
  private final FoodService foods;
  private final DonorService donors;
  private final StorageLocationService locations;
  private final FamilyService families;
  private final DonationService donations;
  private final MonitoringService monitoring;
  private final Clock clock;

  DemoDataInitializer(
      DemoProperties properties,
      Environment environment,
      OrganizationService organizations,
      UserService users,
      CommunityService communities,
      FoodService foods,
      DonorService donors,
      StorageLocationService locations,
      FamilyService families,
      DonationService donations,
      MonitoringService monitoring,
      Clock clock) {
    this.properties = properties;
    this.environment = environment;
    this.organizations = organizations;
    this.users = users;
    this.communities = communities;
    this.foods = foods;
    this.donors = donors;
    this.locations = locations;
    this.families = families;
    this.donations = donations;
    this.monitoring = monitoring;
    this.clock = clock;
  }

  @Override
  public void run(ApplicationArguments args) {
    if (environment.acceptsProfiles(Profiles.of("prod"))) {
      log.warn("Demo data is never created in the prod profile");
      return;
    }
    if (properties.password() == null || properties.password().length() < 10) {
      throw new IllegalStateException(
          "RIBEIRA_DEMO_PASSWORD must have at least 10 characters when demo data is enabled");
    }
    if (organizations.anyExists()) {
      log.info("Database already has data; demo data skipped");
      return;
    }
    UUID organizationId = organizations.createOrganization(ORGANIZATION_NAME);
    UUID adminId =
        users.createInitialAdministrator(
            organizationId,
            "admin" + EMAIL_DOMAIN,
            "Pessoa Demo Administração",
            properties.password());
    runAs(adminId, organizationId, () -> seed(organizationId));
    log.info("Demo data created; users *{} share the configured demo password", EMAIL_DOMAIN);
  }

  private void seed(UUID organizationId) {
    UUID alfa =
        communities
            .create(
                new CommunityDetails(
                    "Comunidade Demo Alfa",
                    "Comunidade fictícia para demonstração",
                    "Município Fictício",
                    "Margem esquerda do rio (fictício)",
                    true))
            .id();
    UUID beta =
        communities
            .create(
                new CommunityDetails(
                    "Comunidade Demo Beta",
                    "Comunidade fictícia para demonstração",
                    null,
                    null,
                    true))
            .id();

    createUser(
        "coordenacao", "Pessoa Demo Coordenação", Map.of(alfa, COORDINATOR, beta, COORDINATOR));
    createUser("estoque", "Pessoa Demo Estoque", Map.of(alfa, STOCK_KEEPER));
    createUser("distribuicao", "Pessoa Demo Distribuição", Map.of(alfa, DISTRIBUTOR));
    createUser("consulta", "Pessoa Demo Consulta", Map.of(alfa, VIEWER, beta, VIEWER));

    FoodView rice = food("Arroz", FoodCategory.GRAINS_AND_CEREALS, UnitOfMeasure.KILOGRAM, 30);
    FoodView beans = food("Feijão", FoodCategory.BEANS_AND_LEGUMES, UnitOfMeasure.KILOGRAM, 30);
    food("Farinha de mandioca", FoodCategory.FLOURS, UnitOfMeasure.KILOGRAM, 15);
    FoodView milk = food("Leite em pó", FoodCategory.DAIRY, UnitOfMeasure.PACKAGE, 20);
    food("Sardinha em lata", FoodCategory.CANNED_GOODS, UnitOfMeasure.UNIT, 30);
    food("Óleo de soja", FoodCategory.OILS_AND_FATS, UnitOfMeasure.LITER, 30);

    UUID donor = donors.create(DonorType.ORGANIZATION, "Doador Demo Mercado", "Fictício").id();
    donors.create(DonorType.ORGANIZATION, "Doador Demo Associação", "Fictício");

    StorageLocationView depotAlfa = locations.create(alfa, "Depósito Demo Central", null);
    locations.create(beta, "Depósito Demo Beta", null);

    for (int i = 1; i <= 12; i++) {
      families.create(
          alfa,
          new FamilyDetails(String.format("DEMO-%03d", i), 2 + (i % 6), FamilyStatus.ACTIVE, null));
    }

    monitoring.createCondition(
        new ConditionSpec(
            null,
            FoodCategory.GRAINS_AND_CEREALS.name(),
            null,
            new BigDecimal("30"),
            null,
            new BigDecimal("70"),
            7,
            "Valores ilustrativos para demonstração — substituir por orientação técnica",
            true));

    LocalDate today = LocalDate.now(clock.withZone(ZoneOffset.UTC));
    List<ReceiveDonation.Item> items = new ArrayList<>();
    items.add(item(rice, "50", depotAlfa.id(), today.plusMonths(6)));
    items.add(item(beans, "30", depotAlfa.id(), today.plusDays(5)));
    items.add(item(milk, "20", depotAlfa.id(), today.plusMonths(3)));
    donations.receive(
        new ReceiveDonation(
            alfa,
            UUID.randomUUID(),
            donor,
            null,
            Instant.now(clock),
            "Doação fictícia de demonstração",
            items));
  }

  private ReceiveDonation.Item item(
      FoodView food, String quantity, UUID location, LocalDate expiration) {
    BigDecimal amount = new BigDecimal(quantity);
    return new ReceiveDonation.Item(
        UUID.randomUUID(),
        food.id(),
        amount,
        food.unit(),
        null,
        List.of(
            new ReceiveDonation.BatchSplit(
                UUID.randomUUID(), amount, location, ExpirationStatus.KNOWN, expiration, null)));
  }

  private FoodView food(String name, FoodCategory category, UnitOfMeasure unit, int warningDays) {
    return foods.create(unit, new FoodDetails(name, category, null, null, warningDays, true));
  }

  private void createUser(String alias, String name, Map<UUID, UUID> roles) {
    users.create(new NewUser(alias + EMAIL_DOMAIN, name, properties.password(), Set.of(), roles));
  }

  /** Executes as the demo administrator so services apply their normal authorization. */
  private static void runAs(UUID userId, UUID organizationId, Runnable action) {
    Jwt jwt =
        Jwt.withTokenValue("demo-seed")
            .header("alg", "none")
            .subject(userId.toString())
            .claim(AccessControl.ORGANIZATION_CLAIM, organizationId.toString())
            .build();
    SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
    try {
      action.run();
    } finally {
      SecurityContextHolder.clearContext();
    }
  }
}
