package com.ribeira.monitoring.api;

import com.ribeira.monitoring.application.MonitoringService;
import com.ribeira.monitoring.application.MonitoringService.ConditionView;
import com.ribeira.monitoring.application.MonitoringService.ReadingView;
import com.ribeira.monitoring.application.MonitoringService.RecordReading;
import com.ribeira.monitoring.domain.StorageCondition.ConditionSpec;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/monitoring")
@Tag(name = "Monitoramento")
public class MonitoringController {

  private final MonitoringService service;

  public MonitoringController(MonitoringService service) {
    this.service = service;
  }

  @PostMapping("/readings")
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(
      summary = "Registra temperatura, umidade e/ou inspeção (MONITORING_WRITE)",
      description =
          "Leituras fora das condições configuradas geram alerta recomendando inspeção. O sistema"
              + " não conclui que o alimento está impróprio.")
  public ReadingView record(@Valid @RequestBody RecordReading request) {
    return service.recordReading(request);
  }

  @GetMapping("/readings")
  public Page<ReadingView> readings(
      @RequestParam UUID communityId,
      @RequestParam(required = false) UUID storageLocationId,
      @PageableDefault(size = 50, sort = "recordedAt", direction = Sort.Direction.DESC)
          Pageable pageable) {
    return service.readings(communityId, storageLocationId, pageable);
  }

  @GetMapping("/conditions")
  public List<ConditionView> conditions() {
    return service.conditions();
  }

  @PostMapping("/conditions")
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(summary = "Configura condição de armazenamento (COMMUNITY_MANAGE)")
  public ConditionView create(@Valid @RequestBody ConditionRequest request) {
    return service.createCondition(request.toSpec());
  }

  @PutMapping("/conditions/{id}")
  public ConditionView update(@PathVariable UUID id, @Valid @RequestBody ConditionRequest request) {
    return service.updateCondition(id, request.toSpec());
  }

  public record ConditionRequest(
      UUID storageLocationId,
      @Size(max = 32) String foodCategory,
      @DecimalMin("-50") @DecimalMax("80") BigDecimal minTemperature,
      @DecimalMin("-50") @DecimalMax("80") BigDecimal maxTemperature,
      @DecimalMin("0") @DecimalMax("100") BigDecimal minHumidity,
      @DecimalMin("0") @DecimalMax("100") BigDecimal maxHumidity,
      @Min(1) @Max(365) Integer maxDaysWithoutInspection,
      @NotBlank @Size(max = 500) String guidanceSource,
      Boolean active) {

    ConditionSpec toSpec() {
      return new ConditionSpec(
          storageLocationId,
          foodCategory,
          minTemperature,
          maxTemperature,
          minHumidity,
          maxHumidity,
          maxDaysWithoutInspection,
          guidanceSource,
          active == null || active);
    }
  }
}
