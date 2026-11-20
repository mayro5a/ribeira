package com.ribeira.auth.api;

import com.ribeira.auth.application.UserService;
import com.ribeira.auth.application.UserService.NewUser;
import com.ribeira.auth.application.UserService.UserView;
import com.ribeira.auth.domain.UserStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;
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
@RequestMapping("/api/v1/users")
@Tag(name = "Usuários", description = "Exige USER_MANAGE na organização")
public class UserController {

  private final UserService service;

  public UserController(UserService service) {
    this.service = service;
  }

  @GetMapping
  public List<UserView> list() {
    return service.list();
  }

  @GetMapping("/{id}")
  public UserView get(@PathVariable UUID id) {
    return service.get(id);
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(summary = "Cadastra usuário com papéis organizacionais e por comunidade")
  public UserView create(@Valid @RequestBody CreateUserRequest request) {
    return service.create(
        new NewUser(
            request.email(),
            request.displayName(),
            request.password(),
            orEmpty(request.organizationRoleIds()),
            orEmpty(request.communityRoles())));
  }

  @PutMapping("/{id}")
  @Operation(summary = "Atualiza nome e situação (ACTIVE, BLOCKED, DISABLED)")
  public UserView update(@PathVariable UUID id, @Valid @RequestBody UpdateUserRequest request) {
    return service.update(id, request.displayName(), request.status());
  }

  @PutMapping("/{id}/access")
  @Operation(summary = "Substitui os papéis do usuário")
  public UserView assignAccess(@PathVariable UUID id, @RequestBody AccessRequest request) {
    return service.assignAccess(
        id, orEmpty(request.organizationRoleIds()), orEmpty(request.communityRoles()));
  }

  @PostMapping("/{id}/password")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Operation(summary = "Redefine a senha e encerra as sessões do usuário")
  public void resetPassword(@PathVariable UUID id, @Valid @RequestBody PasswordRequest request) {
    service.resetPassword(id, request.password());
  }

  private static <T> Set<T> orEmpty(Set<T> set) {
    return set == null ? Set.of() : set;
  }

  private static <K, V> Map<K, V> orEmpty(Map<K, V> map) {
    return map == null ? Map.of() : map;
  }

  public record CreateUserRequest(
      @NotBlank @Email @Size(max = 254) String email,
      @NotBlank @Size(max = 120) String displayName,
      @NotBlank @Size(min = 10, max = 200) String password,
      Set<UUID> organizationRoleIds,
      Map<UUID, UUID> communityRoles) {}

  public record UpdateUserRequest(
      @NotBlank @Size(max = 120) String displayName, @NotNull UserStatus status) {}

  public record AccessRequest(Set<UUID> organizationRoleIds, Map<UUID, UUID> communityRoles) {}

  public record PasswordRequest(@NotBlank @Size(min = 10, max = 200) String password) {}
}
