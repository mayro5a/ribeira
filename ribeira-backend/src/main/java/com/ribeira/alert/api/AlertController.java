package com.ribeira.alert.api;

import com.ribeira.alert.application.AlertService;
import com.ribeira.alert.application.AlertService.AlertView;
import com.ribeira.alert.domain.Alert.AlertStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/alerts")
@Tag(name = "Alertas")
public class AlertController {

  private final AlertService service;

  public AlertController(AlertService service) {
    this.service = service;
  }

  @GetMapping
  public Page<AlertView> list(
      @RequestParam UUID communityId,
      @RequestParam(required = false) AlertStatus status,
      @PageableDefault(size = 50, sort = "createdAt", direction = Sort.Direction.DESC)
          Pageable pageable) {
    return service.list(communityId, status, pageable);
  }

  @PostMapping("/{id}/resolve")
  @Operation(summary = "Registra inspeção e resolução do alerta (ALERT_RESOLVE)")
  public AlertView resolve(@PathVariable UUID id, @Valid @RequestBody ResolveRequest request) {
    return service.resolve(id, request.notes(), Boolean.TRUE.equals(request.inspectionPerformed()));
  }

  @PostMapping("/evaluate")
  @Operation(summary = "Executa as regras de alerta agora para a comunidade")
  public Map<String, Integer> evaluate(@RequestParam UUID communityId) {
    return Map.of("opened", service.evaluate(communityId));
  }

  public record ResolveRequest(
      @NotBlank @Size(max = 1000) String notes, Boolean inspectionPerformed) {}
}
