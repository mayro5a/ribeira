package com.ribeira.donation.api;

import com.ribeira.donation.application.DonationService;
import com.ribeira.donation.application.DonationService.DonationReceipt;
import com.ribeira.donation.application.DonationView;
import com.ribeira.donation.application.ReceiveDonation;
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
@RequestMapping("/api/v1/donations")
@Tag(name = "Doações")
public class DonationController {

  private final DonationService service;

  public DonationController(DonationService service) {
    this.service = service;
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(
      summary = "Registra doação recebida e gera lotes rastreáveis (DONATION_WRITE)",
      description =
          "IDs de doação, itens e lotes são UUIDs gerados pelo cliente. A soma dos lotes de cada"
              + " item deve ser igual à quantidade do item. Erros: 409 DUPLICATE_ID, 422"
              + " ORIGIN_REQUIRED, 422 BATCH_SPLIT_MISMATCH, 422 UNIT_MISMATCH.")
  public DonationReceipt receive(@Valid @RequestBody ReceiveDonation request) {
    return service.receive(request);
  }

  @GetMapping
  @Operation(summary = "Histórico de doações da comunidade, paginado")
  public Page<DonationView> list(
      @RequestParam UUID communityId,
      @RequestParam(required = false) Instant from,
      @RequestParam(required = false) Instant to,
      @PageableDefault(size = 50, sort = "receivedAt", direction = Sort.Direction.DESC)
          Pageable pageable) {
    return service.list(communityId, from, to, pageable);
  }

  @GetMapping("/{id}")
  public DonationView get(@PathVariable UUID id) {
    return service.get(id);
  }
}
