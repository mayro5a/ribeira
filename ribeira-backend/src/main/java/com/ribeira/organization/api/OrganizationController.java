package com.ribeira.organization.api;

import com.ribeira.organization.application.OrganizationService;
import com.ribeira.organization.application.OrganizationService.OrganizationView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/organizations")
@Tag(name = "Organizações")
public class OrganizationController {

  private final OrganizationService service;

  public OrganizationController(OrganizationService service) {
    this.service = service;
  }

  @GetMapping("/{id}")
  @Operation(summary = "Consulta a organização do usuário autenticado")
  public OrganizationView get(@PathVariable UUID id) {
    return service.get(id);
  }

  @PutMapping("/{id}")
  @Operation(summary = "Atualiza o nome da organização (ORGANIZATION_MANAGE)")
  public OrganizationView update(
      @PathVariable UUID id, @Valid @RequestBody UpdateOrganizationRequest request) {
    return service.rename(id, request.name());
  }

  public record UpdateOrganizationRequest(@NotBlank @Size(max = 160) String name) {}
}
