package com.ribeira.inventory.api;

import com.ribeira.inventory.application.InventoryCommands.Adjustment;
import com.ribeira.inventory.application.InventoryCommands.BatchEntry;
import com.ribeira.inventory.application.InventoryService;
import com.ribeira.inventory.application.InventoryViews.BatchView;
import com.ribeira.inventory.application.InventoryViews.DivergenceView;
import com.ribeira.inventory.application.InventoryViews.InventoryItemView;
import com.ribeira.inventory.application.InventoryViews.LowStockView;
import com.ribeira.inventory.application.InventoryViews.MovementView;
import com.ribeira.inventory.domain.InventoryEnums.BatchStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@Tag(name = "Estoque")
public class InventoryController {

  private final InventoryService service;

  public InventoryController(InventoryService service) {
    this.service = service;
  }

  @GetMapping("/api/v1/communities/{communityId}/inventory")
  @Operation(summary = "Saldo por alimento da comunidade (STOCK_READ)")
  public List<InventoryItemView> summary(@PathVariable UUID communityId) {
    return service.summary(communityId);
  }

  @GetMapping("/api/v1/inventory/batches")
  @Operation(summary = "Lotes da comunidade, paginados (até 200 por página)")
  public Page<BatchView> batches(
      @RequestParam UUID communityId,
      @RequestParam(required = false) UUID foodId,
      @RequestParam(required = false) BatchStatus status,
      @PageableDefault(size = 50, sort = "receivedAt", direction = Sort.Direction.DESC)
          Pageable pageable) {
    return service.batches(communityId, foodId, status, pageable);
  }

  @GetMapping("/api/v1/inventory/batches/{id}")
  public BatchView batch(@PathVariable UUID id) {
    return service.batch(id);
  }

  @PostMapping("/api/v1/inventory/batches")
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(
      summary = "Entrada de estoque sem doação registrada (STOCK_WRITE)",
      description =
          "Exige descrição da origem. O ID do lote é gerado pelo cliente (UUID). Erros: 409"
              + " DUPLICATE_ID, 422 UNIT_MISMATCH, 422 INVALID_QUANTITY_PRECISION.")
  public BatchView registerEntry(@Valid @RequestBody BatchEntry request) {
    return service.registerEntry(request);
  }

  @PutMapping("/api/v1/inventory/batches/{id}/status")
  @Operation(summary = "Bloqueia para inspeção ou libera um lote (STOCK_WRITE)")
  public BatchView changeStatus(@PathVariable UUID id, @Valid @RequestBody StatusRequest request) {
    return service.changeStatus(id, request.status(), request.reason());
  }

  @PostMapping("/api/v1/inventory/movements")
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(
      summary = "Ajuste justificado de estoque (STOCK_ADJUST)",
      description =
          "Somente ajustes são criados por aqui; entradas, distribuições e perdas geram suas"
              + " movimentações nos próprios fluxos. Erro 409 INSUFFICIENT_STOCK se o saldo ficar"
              + " negativo.")
  public MovementView adjust(@Valid @RequestBody Adjustment request) {
    return service.adjust(request);
  }

  @GetMapping("/api/v1/inventory/movements")
  @Operation(summary = "Livro-razão de movimentações, paginado")
  public Page<MovementView> movements(
      @RequestParam UUID communityId,
      @RequestParam(required = false) UUID batchId,
      @PageableDefault(size = 50, sort = "recordedAt", direction = Sort.Direction.DESC)
          Pageable pageable) {
    return service.movements(communityId, batchId, pageable);
  }

  @GetMapping("/api/v1/inventory/expiring")
  @Operation(summary = "Lotes com validade conhecida vencendo nos próximos dias")
  public List<BatchView> expiring(
      @RequestParam UUID communityId,
      @RequestParam(defaultValue = "7") @Min(0) @Max(365) int days) {
    return service.expiring(communityId, days);
  }

  @GetMapping("/api/v1/inventory/low-stock")
  @Operation(summary = "Alimentos abaixo do estoque mínimo configurado")
  public List<LowStockView> lowStock(@RequestParam UUID communityId) {
    return service.lowStock(communityId);
  }

  @GetMapping("/api/v1/inventory/fefo")
  @Operation(
      summary = "Ordem sugerida de uso (FEFO)",
      description = "Sugestão: a pessoa responsável pode escolher outros lotes.")
  public List<BatchView> fefo(@RequestParam UUID communityId, @RequestParam UUID foodId) {
    return service.fefoSuggestion(communityId, foodId);
  }

  @GetMapping("/api/v1/inventory/reconciliation")
  @Operation(summary = "Lotes cujo saldo diverge do livro-razão (esperado: lista vazia)")
  public List<DivergenceView> reconciliation(@RequestParam UUID communityId) {
    return service.reconciliation(communityId);
  }

  @PutMapping("/api/v1/communities/{communityId}/stock-minimums/{foodId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Operation(summary = "Define o estoque mínimo de um alimento na comunidade (STOCK_WRITE)")
  public void setMinimum(
      @PathVariable UUID communityId,
      @PathVariable UUID foodId,
      @Valid @RequestBody MinimumRequest request) {
    service.setMinimum(communityId, foodId, request.minimumQuantity());
  }

  public record StatusRequest(@NotNull BatchStatus status, @Size(max = 500) String reason) {}

  public record MinimumRequest(@NotNull @PositiveOrZero BigDecimal minimumQuantity) {}
}
