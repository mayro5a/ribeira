package com.ribeira.auth.api;

import com.ribeira.auth.application.RoleService;
import com.ribeira.auth.application.RoleService.RoleView;
import com.ribeira.shared.security.Permission;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Set;
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
@RequestMapping("/api/v1/roles")
@Tag(name = "Papéis e permissões")
public class RoleController {

  private final RoleService service;

  public RoleController(RoleService service) {
    this.service = service;
  }

  @GetMapping
  @Operation(summary = "Lista papéis padrão do sistema e papéis próprios da organização")
  public List<RoleView> list() {
    return service.list();
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(summary = "Cria papel próprio da organização (USER_MANAGE)")
  public RoleView create(@Valid @RequestBody CreateRoleRequest request) {
    return service.create(
        request.code(), request.name(), request.description(), request.permissions());
  }

  @PutMapping("/{id}")
  @Operation(summary = "Altera papel próprio da organização (USER_MANAGE)")
  public RoleView update(@PathVariable UUID id, @Valid @RequestBody UpdateRoleRequest request) {
    return service.update(id, request.name(), request.description(), request.permissions());
  }

  public record CreateRoleRequest(
      @NotBlank @Pattern(regexp = "[A-Z][A-Z0-9_]{1,47}") String code,
      @NotBlank @Size(max = 80) String name,
      @Size(max = 255) String description,
      @NotEmpty Set<Permission> permissions) {}

  public record UpdateRoleRequest(
      @NotBlank @Size(max = 80) String name,
      @Size(max = 255) String description,
      @NotEmpty Set<Permission> permissions) {}
}
