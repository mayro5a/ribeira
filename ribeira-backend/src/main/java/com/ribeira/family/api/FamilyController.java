package com.ribeira.family.api;

import com.ribeira.family.application.FamilyService;
import com.ribeira.family.application.FamilyService.FamilyView;
import com.ribeira.family.domain.Family.FamilyDetails;
import com.ribeira.family.domain.Family.FamilyStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(
    name = "Famílias",
    description =
        "Identificação por código comunitário; não registre nomes, documentos ou endereços.")
public class FamilyController {

  private final FamilyService service;

  public FamilyController(FamilyService service) {
    this.service = service;
  }

  @GetMapping("/api/v1/communities/{communityId}/families")
  @Operation(summary = "Famílias da comunidade (FAMILY_READ)")
  public List<FamilyView> list(@PathVariable UUID communityId) {
    return service.list(communityId);
  }

  @PostMapping("/api/v1/communities/{communityId}/families")
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(summary = "Cadastra família (FAMILY_WRITE)")
  public FamilyView create(
      @PathVariable UUID communityId, @Valid @RequestBody FamilyRequest request) {
    return service.create(communityId, request.toDetails());
  }

  @GetMapping("/api/v1/families/{id}")
  public FamilyView get(@PathVariable UUID id) {
    return service.get(id);
  }

  @PutMapping("/api/v1/families/{id}")
  public FamilyView update(@PathVariable UUID id, @Valid @RequestBody FamilyRequest request) {
    return service.update(id, request.toDetails());
  }

  public record FamilyRequest(
      @NotBlank @Size(max = 40) String code,
      @Min(1) @Max(50) int memberCount,
      FamilyStatus status,
      @Size(max = 500) String notes) {

    FamilyDetails toDetails() {
      return new FamilyDetails(
          code, memberCount, status == null ? FamilyStatus.ACTIVE : status, notes);
    }
  }
}
