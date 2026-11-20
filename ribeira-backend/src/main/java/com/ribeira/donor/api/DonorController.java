package com.ribeira.donor.api;

import com.ribeira.donor.application.DonorService;
import com.ribeira.donor.application.DonorService.DonorView;
import com.ribeira.donor.domain.Donor.DonorType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
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
@RequestMapping("/api/v1/donors")
@Tag(name = "Doadores")
public class DonorController {

  private final DonorService service;

  public DonorController(DonorService service) {
    this.service = service;
  }

  @GetMapping
  public List<DonorView> list() {
    return service.list();
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(summary = "Cadastra doador (DONOR_MANAGE)")
  public DonorView create(@Valid @RequestBody DonorRequest request) {
    return service.create(request.type(), request.name(), request.notes());
  }

  @PutMapping("/{id}")
  @Operation(summary = "Atualiza doador (DONOR_MANAGE)")
  public DonorView update(@PathVariable UUID id, @Valid @RequestBody DonorRequest request) {
    return service.update(
        id,
        request.type(),
        request.name(),
        request.notes(),
        request.active() == null || request.active());
  }

  public record DonorRequest(
      @NotNull DonorType type,
      @NotBlank @Size(max = 160) String name,
      @Size(max = 500) String notes,
      Boolean active) {}
}
