package com.ribeira.support;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Creates tenants, communities and users directly in the database so each test starts from an
 * explicit, isolated state. Every name is unique, so tests never depend on each other's data.
 */
public class Fixtures {

  public static final String PASSWORD = "senha-de-teste-123";
  public static final UUID ORG_ADMIN = UUID.fromString("00000000-0000-0000-0000-000000000101");
  public static final UUID COORDINATOR = UUID.fromString("00000000-0000-0000-0000-000000000102");
  public static final UUID STOCK_KEEPER = UUID.fromString("00000000-0000-0000-0000-000000000103");
  public static final UUID DISTRIBUTOR = UUID.fromString("00000000-0000-0000-0000-000000000104");
  public static final UUID VIEWER = UUID.fromString("00000000-0000-0000-0000-000000000105");

  private final JdbcTemplate jdbc;
  private final String passwordHash;

  public Fixtures(JdbcTemplate jdbc, PasswordEncoder encoder) {
    this.jdbc = jdbc;
    this.passwordHash = encoder.encode(PASSWORD);
  }

  public UUID organization() {
    UUID id = UUID.randomUUID();
    Timestamp now = Timestamp.from(Instant.now());
    jdbc.update(
        "INSERT INTO organizations (id, name, created_at, updated_at) VALUES (?, ?, ?, ?)",
        id,
        "Organização " + id,
        now,
        now);
    return id;
  }

  public UUID community(UUID organizationId) {
    UUID id = UUID.randomUUID();
    Timestamp now = Timestamp.from(Instant.now());
    jdbc.update(
        "INSERT INTO communities (id, organization_id, name, created_at, updated_at)"
            + " VALUES (?, ?, ?, ?, ?)",
        id,
        organizationId,
        "Comunidade " + id,
        now,
        now);
    return id;
  }

  /** Creates an active user; returns its e-mail (password is {@link #PASSWORD}). */
  public TestUser user(UUID organizationId, UUID organizationRole, Map<UUID, UUID> communityRoles) {
    UUID id = UUID.randomUUID();
    String email = "user-" + id + "@teste.ribeira.local";
    Timestamp now = Timestamp.from(Instant.now());
    jdbc.update(
        "INSERT INTO users (id, organization_id, email, display_name, password_hash, status,"
            + " created_at, updated_at) VALUES (?, ?, ?, ?, ?, 'ACTIVE', ?, ?)",
        id,
        organizationId,
        email,
        "Usuário de teste",
        passwordHash,
        now,
        now);
    if (organizationRole != null) {
      jdbc.update("INSERT INTO user_roles (user_id, role_id) VALUES (?, ?)", id, organizationRole);
    }
    communityRoles.forEach(
        (community, role) ->
            jdbc.update(
                "INSERT INTO community_users (user_id, community_id, role_id) VALUES (?, ?, ?)",
                id,
                community,
                role));
    return new TestUser(id, organizationId, email);
  }

  public TestUser admin(UUID organizationId) {
    return user(organizationId, ORG_ADMIN, Map.of());
  }

  public TestUser member(UUID organizationId, UUID communityId, UUID role) {
    return user(organizationId, null, Map.of(communityId, role));
  }

  public record TestUser(UUID id, UUID organizationId, String email) {}
}
