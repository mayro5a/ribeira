package com.ribeira.distribution.api;

import com.ribeira.distribution.application.CreateDistribution;
import com.ribeira.distribution.application.DistributionService;
import com.ribeira.distribution.application.DistributionView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/distributions")
@Tag(name = "Distribuições")
public class DistributionController {

  private final DistributionService service;

  public DistributionController(DistributionService service) {
    this.service = service;
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(
      summary = "Registra distribuição às famílias (DISTRIBUTION_WRITE)",
      description =
          "Operação atômica: se algum lote não tiver saldo, nada é registrado. Erros: 409"
              + " INSUFFICIENT_STOCK, 409 BATCH_BLOCKED, 409 DUPLICATE_ID, 422 FAMILY_INACTIVE.")
  public DistributionView create(@Valid @RequestBody CreateDistribution request) {
    return service.create(request);
  }

  @GetMapping
  @Operation(summary = "Histórico de distribuições (filtro por família exige FAMILY_READ)")
  public Page<DistributionView> list(
      @RequestParam UUID communityId,
      @RequestParam(required = false) UUID familyId,
      @RequestParam(required = false) Instant from,
      @RequestParam(required = false) Instant to,
      @PageableDefault(size = 50, sort = "distributedAt", direction = Sort.Direction.DESC)
          Pageable pageable) {
    return service.list(communityId, familyId, from, to, pageable);
  }

  @GetMapping("/{id}")
  @Operation(summary = "Distribuição (base do comprovante)")
  public DistributionView get(@PathVariable UUID id) {
    return service.get(id);
  }
}
