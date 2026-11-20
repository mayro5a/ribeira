package com.ribeira.food.application;

import com.ribeira.food.domain.Food;
import com.ribeira.food.domain.Food.FoodDetails;
import com.ribeira.food.domain.FoodCategory;
import com.ribeira.food.domain.FoodRepository;
import com.ribeira.shared.audit.AuditRecorder;
import com.ribeira.shared.domain.UnitOfMeasure;
import com.ribeira.shared.exception.BusinessRuleException;
import com.ribeira.shared.exception.ResourceNotFoundException;
import com.ribeira.shared.exception.StateConflictException;
import com.ribeira.shared.security.AccessControl;
import com.ribeira.shared.security.Actor;
import com.ribeira.shared.security.Permission;
import com.ribeira.shared.sync.ChangeTracker;
import com.ribeira.shared.sync.SyncEntityType;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FoodService {

  private final FoodRepository foods;
  private final AccessControl accessControl;
  private final AuditRecorder audit;
  private final ChangeTracker changes;

  public FoodService(
      FoodRepository foods,
      AccessControl accessControl,
      AuditRecorder audit,
      ChangeTracker changes) {
    this.foods = foods;
    this.accessControl = accessControl;
    this.audit = audit;
    this.changes = changes;
  }

  /** The catalog contains no personal data; any member of the organization may read it. */
  @Transactional(readOnly = true)
  public List<FoodView> list() {
    Actor actor = accessControl.member();
    return foods.findByOrganizationIdOrderByName(actor.organizationId()).stream()
        .map(FoodView::of)
        .toList();
  }

  @Transactional
  public FoodView create(UnitOfMeasure unit, FoodDetails details) {
    Actor actor = accessControl.requireAnywhere(Permission.FOOD_MANAGE);
    UUID id = UUID.randomUUID();
    ensureUniqueName(actor.organizationId(), details.name(), id);
    Food food = foods.save(new Food(id, actor.organizationId(), unit, details));
    publish(actor, food, "FOOD_CREATED");
    return FoodView.of(food);
  }

  @Transactional
  public FoodView update(UUID id, FoodDetails details) {
    Actor actor = accessControl.requireAnywhere(Permission.FOOD_MANAGE);
    Food food =
        foods
            .findByIdAndOrganizationId(id, actor.organizationId())
            .orElseThrow(() -> new ResourceNotFoundException("Alimento", id));
    ensureUniqueName(actor.organizationId(), details.name(), id);
    food.update(details);
    publish(actor, food, "FOOD_UPDATED");
    return FoodView.of(food);
  }

  /**
   * For other modules registering stock: the food must belong to the organization and be active.
   */
  @Transactional(readOnly = true)
  public FoodInfo requireUsable(UUID organizationId, UUID foodId) {
    Food food =
        foods
            .findByIdAndOrganizationId(foodId, organizationId)
            .orElseThrow(() -> new ResourceNotFoundException("Alimento", foodId));
    if (!food.isActive()) {
      throw new BusinessRuleException(
          "FOOD_INACTIVE", "O alimento " + food.getName() + " está inativo.");
    }
    return FoodInfo.of(food);
  }

  @Transactional(readOnly = true)
  public Map<UUID, FoodInfo> infoOf(UUID organizationId, Collection<UUID> foodIds) {
    if (foodIds.isEmpty()) {
      return Map.of();
    }
    return foods.findByOrganizationIdAndIdIn(organizationId, foodIds).stream()
        .map(FoodInfo::of)
        .collect(Collectors.toMap(FoodInfo::id, Function.identity()));
  }

  private void publish(Actor actor, Food food, String action) {
    audit.record(
        actor.organizationId(), null, actor.userId(), action, "FOOD", food.getId(), Map.of());
    changes.record(actor.organizationId(), null, SyncEntityType.FOOD, food.getId());
  }

  private void ensureUniqueName(UUID organizationId, String name, UUID id) {
    if (foods.existsByOrganizationIdAndNameIgnoreCaseAndIdNot(organizationId, name.trim(), id)) {
      throw new StateConflictException("FOOD_NAME_IN_USE", "Já existe um alimento com este nome.");
    }
  }

  public record FoodInfo(
      UUID id,
      String name,
      FoodCategory category,
      UnitOfMeasure unit,
      int expirationWarningDays,
      boolean active) {
    static FoodInfo of(Food food) {
      return new FoodInfo(
          food.getId(),
          food.getName(),
          food.getCategory(),
          food.getUnit(),
          food.getExpirationWarningDays(),
          food.isActive());
    }
  }
}
