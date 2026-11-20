package com.ribeira.loss.api;

import com.ribeira.loss.application.LossService;
import com.ribeira.loss.application.LossService.LossView;
import com.ribeira.loss.application.LossService.RecordLoss;
import com.ribeira.loss.domain.FoodLoss.LossReason;
import com.ribeira.loss.domain.FoodLoss.LossStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
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
@RequestMapping("/api/v1/losses")
@Tag(name = "Perdas")
public class LossController {

  private final LossService service;

  public LossController(LossService service) {
    this.service = service;
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(
      summary = "Registra perda vinculada a um lote (LOSS_WRITE)",
      description =
          "Gera movimentação LOSS na mesma transação. O sistema não declara alimentos impróprios;"
              + " recomenda inspeção por pessoa responsável.")
  public LossView record(@Valid @RequestBody RecordLoss request) {
    return service.record(request);
  }

  @GetMapping
  public Page<LossView> list(
      @RequestParam UUID communityId,
      @RequestParam(required = false) LossReason reason,
      @RequestParam(required = false) Instant from,
      @RequestParam(required = false) Instant to,
      @PageableDefault(size = 50, sort = "occurredAt", direction = Sort.Direction.DESC)
          Pageable pageable) {
    return service.list(communityId, reason, from, to, pageable);
  }

  @PutMapping("/{id}/status")
  @Operation(summary = "Atualiza a situação da ocorrência")
  public LossView changeStatus(@PathVariable UUID id, @Valid @RequestBody StatusRequest request) {
    return service.changeStatus(id, request.status());
  }

  public record StatusRequest(@NotNull LossStatus status) {}
}
