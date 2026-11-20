package com.ribeira.food.application;

import com.ribeira.food.domain.Food;
import com.ribeira.food.domain.FoodCategory;
import com.ribeira.shared.domain.UnitOfMeasure;
import java.util.UUID;

public record FoodView(
    UUID id,
    String name,
    FoodCategory category,
    UnitOfMeasure unit,
    String description,
    String storageGuidance,
    int expirationWarningDays,
    boolean active) {

  static FoodView of(Food food) {
    return new FoodView(
        food.getId(),
        food.getName(),
        food.getCategory(),
        food.getUnit(),
        food.getDescription(),
        food.getStorageGuidance(),
        food.getExpirationWarningDays(),
        food.isActive());
  }
}
