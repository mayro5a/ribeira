package com.ribeira.sync.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ribeira.distribution.application.CreateDistribution;
import com.ribeira.distribution.application.DistributionService;
import com.ribeira.donation.application.DonationService;
import com.ribeira.donation.application.ReceiveDonation;
import com.ribeira.inventory.application.InventoryCommands.Adjustment;
import com.ribeira.inventory.application.InventoryCommands.BatchEntry;
import com.ribeira.inventory.application.InventoryService;
import com.ribeira.loss.application.LossService;
import com.ribeira.loss.application.LossService.RecordLoss;
import com.ribeira.monitoring.application.MonitoringService;
import com.ribeira.monitoring.application.MonitoringService.RecordReading;
import com.ribeira.shared.exception.BusinessRuleException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * Maps each offline operation type to the same application use case the REST API uses, so both
 * paths enforce identical rules and authorization (docs/synchronization.md section 2.1).
 */
@Component
public class OperationHandlers {

  public static final int SUPPORTED_SCHEMA_VERSION = 1;

  private final Map<String, Handler<?>> handlers;
  private final ObjectMapper mapper;
  private final Validator validator;

  public OperationHandlers(
      DonationService donations,
      InventoryService inventory,
      DistributionService distributions,
      LossService losses,
      MonitoringService monitoring,
      ObjectMapper mapper,
      Validator validator) {
    this.mapper = mapper;
    this.validator = validator;
    this.handlers =
        Map.of(
            "DONATION_RECEIVE",
            new Handler<>("DONATION", ReceiveDonation.class, true, donations::receive),
            "BATCH_ENTRY",
            new Handler<>("BATCH", BatchEntry.class, true, inventory::registerEntry),
            "STOCK_ADJUSTMENT",
            new Handler<>("MOVEMENT", Adjustment.class, true, inventory::adjust),
            "DISTRIBUTION_CREATE",
            new Handler<>("DISTRIBUTION", CreateDistribution.class, true, distributions::create),
            "LOSS_RECORD",
            new Handler<>("LOSS", RecordLoss.class, true, losses::record),
            "READING_RECORD",
            new Handler<>("READING", RecordReading.class, false, monitoring::recordReading));
  }

  /** Validates the envelope against the handler before anything is executed. */
  public Handler<?> resolve(String operationType, String entityType, int schemaVersion) {
    Handler<?> handler = handlers.get(operationType);
    if (handler == null) {
      throw new OperationRejectedException(
          "UNSUPPORTED_OPERATION", "Tipo de operação não suportado: " + operationType);
    }
    if (!handler.entityType().equals(entityType)) {
      throw new OperationRejectedException(
          "ENTITY_TYPE_MISMATCH", "Tipo de entidade incompatível com a operação.");
    }
    if (schemaVersion != SUPPORTED_SCHEMA_VERSION) {
      throw new OperationRejectedException(
          "UNSUPPORTED_SCHEMA_VERSION", "Versão de formato não suportada: " + schemaVersion);
    }
    return handler;
  }

  /** Executes in the caller's transaction. The envelope's community is the only one trusted. */
  public void execute(Handler<?> handler, JsonNode payload, UUID communityId) {
    handler.run(this, payload, communityId);
  }

  <T> T toCommand(Class<T> type, JsonNode payload, UUID communityId) {
    if (payload == null || !payload.isObject()) {
      throw new OperationRejectedException("VALIDATION_FAILED", "Payload ausente ou inválido.");
    }
    ObjectNode node = ((ObjectNode) payload).deepCopy();
    node.put("communityId", communityId.toString());
    T command;
    try {
      command = mapper.treeToValue(node, type);
    } catch (JsonProcessingException e) {
      throw new OperationRejectedException(
          "VALIDATION_FAILED", "Payload em formato inválido: " + e.getOriginalMessage());
    }
    Set<ConstraintViolation<T>> violations = validator.validate(command);
    if (!violations.isEmpty()) {
      throw new OperationRejectedException(
          "VALIDATION_FAILED",
          violations.stream()
              .map(v -> v.getPropertyPath() + ": " + v.getMessage())
              .sorted()
              .collect(Collectors.joining("; ")));
    }
    return command;
  }

  public record Handler<T>(
      String entityType, Class<T> commandType, boolean changesStock, Consumer<T> action) {

    void run(OperationHandlers handlers, JsonNode payload, UUID communityId) {
      action.accept(handlers.toCommand(commandType, payload, communityId));
    }
  }

  /** The operation itself is invalid; retrying it unchanged cannot succeed. */
  public static class OperationRejectedException extends BusinessRuleException {
    public OperationRejectedException(String code, String message) {
      super(code, message);
    }
  }
}
