package com.ribeira.food.api;

import com.ribeira.food.application.FoodService;
import com.ribeira.food.application.FoodView;
import com.ribeira.food.domain.Food.FoodDetails;
import com.ribeira.food.domain.FoodCategory;
import com.ribeira.shared.domain.UnitOfMeasure;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/foods")
@Tag(name = "Alimentos")
public class FoodController {

  private static final int DEFAULT_WARNING_DAYS = 7;

  private final FoodService service;

  public FoodController(FoodService service) {
    this.service = service;
  }

  @GetMapping
  @Operation(summary = "Catálogo de alimentos da organização")
  public List<FoodView> list() {
    return service.list();
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(
      summary = "Cadastra alimento (FOOD_MANAGE)",
      description = "A unidade de medida não pode ser alterada depois do cadastro.")
  public FoodView create(@Valid @RequestBody CreateFoodRequest request) {
    return service.create(request.unit(), request.details().toDetails());
  }

  @PutMapping("/{id}")
  @Operation(summary = "Atualiza alimento (FOOD_MANAGE)")
  public FoodView update(@PathVariable UUID id, @Valid @RequestBody FoodDetailsRequest request) {
    return service.update(id, request.toDetails());
  }

  public record CreateFoodRequest(
      @NotNull UnitOfMeasure unit,
      @NotBlank @Size(max = 120) String name,
      @NotNull FoodCategory category,
      @Size(max = 500) String description,
      @Size(max = 1000) String storageGuidance,
      @Min(0) @Max(365) Integer expirationWarningDays) {

    FoodDetailsRequest details() {
      return new FoodDetailsRequest(
          name, category, description, storageGuidance, expirationWarningDays, true);
    }
  }

  public record FoodDetailsRequest(
      @NotBlank @Size(max = 120) String name,
      @NotNull FoodCategory category,
      @Size(max = 500) String description,
      @Size(max = 1000) String storageGuidance,
      @Min(0) @Max(365) Integer expirationWarningDays,
      Boolean active) {

    FoodDetails toDetails() {
      return new FoodDetails(
          name,
          category,
          description,
          storageGuidance,
          expirationWarningDays == null ? DEFAULT_WARNING_DAYS : expirationWarningDays,
          active == null || active);
    }
  }
}
