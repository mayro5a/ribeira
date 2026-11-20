package com.ribeira.community.api;

import com.ribeira.community.application.CommunityService;
import com.ribeira.community.application.CommunityView;
import com.ribeira.community.domain.Community.CommunityDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/communities")
@Tag(name = "Comunidades")
public class CommunityController {

  private final CommunityService service;

  public CommunityController(CommunityService service) {
    this.service = service;
  }

  @GetMapping
  @Operation(summary = "Lista as comunidades que o usuário pode consultar")
  public List<CommunityView> list() {
    return service.listAccessible();
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(summary = "Cadastra comunidade (COMMUNITY_MANAGE)")
  public CommunityView create(@Valid @RequestBody CommunityRequest request) {
    return service.create(request.toDetails());
  }

  @GetMapping("/{id}")
  @Operation(summary = "Consulta comunidade autorizada")
  public CommunityView get(@PathVariable UUID id) {
    return service.get(id);
  }

  @PutMapping("/{id}")
  @Operation(summary = "Atualiza comunidade (COMMUNITY_MANAGE)")
  public CommunityView update(@PathVariable UUID id, @Valid @RequestBody CommunityRequest request) {
    return service.update(id, request.toDetails());
  }

  @PutMapping("/{id}/primary-device")
  @Operation(
      summary = "Define o aparelho principal (DEVICE_MANAGE)",
      description =
          "Com aparelho principal definido, operações offline que alteram estoque vindas de"
              + " outros aparelhos viram conflito para revisão. Envie deviceId nulo para remover.")
  public CommunityView assignPrimaryDevice(
      @PathVariable UUID id, @RequestBody PrimaryDeviceRequest request) {
    return service.assignPrimaryDevice(id, request.deviceId());
  }

  public record CommunityRequest(
      @NotBlank @Size(max = 160) String name,
      @Size(max = 1000) String description,
      @Size(max = 120) String municipality,
      @Size(max = 255) String approximateLocation,
      Boolean active) {

    CommunityDetails toDetails() {
      return new CommunityDetails(
          name, description, municipality, approximateLocation, active == null || active);
    }
  }

  public record PrimaryDeviceRequest(UUID deviceId) {}
}
