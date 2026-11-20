package com.ribeira.inventory.api;

import com.ribeira.inventory.application.InventoryViews.StorageLocationView;
import com.ribeira.inventory.application.StorageLocationService;
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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Locais de armazenamento")
public class StorageLocationController {

  private final StorageLocationService service;

  public StorageLocationController(StorageLocationService service) {
    this.service = service;
  }

  @GetMapping("/api/v1/communities/{communityId}/storage-locations")
  public List<StorageLocationView> list(@PathVariable UUID communityId) {
    return service.list(communityId);
  }

  @PostMapping("/api/v1/communities/{communityId}/storage-locations")
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(summary = "Cadastra local de armazenamento (STOCK_WRITE)")
  public StorageLocationView create(
      @PathVariable UUID communityId, @Valid @RequestBody LocationRequest request) {
    return service.create(communityId, request.name(), request.description());
  }

  @PutMapping("/api/v1/storage-locations/{id}")
  public StorageLocationView update(
      @PathVariable UUID id, @Valid @RequestBody LocationRequest request) {
    return service.update(
        id, request.name(), request.description(), request.active() == null || request.active());
  }

  public record LocationRequest(
      @NotBlank @Size(max = 120) String name,
      @Size(max = 500) String description,
      Boolean active) {}
}
