package com.ribeira.shared.exception;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

class GlobalExceptionHandlerTest {

  private MockMvc mvc;

  @BeforeEach
  void setUp() {
    mvc =
        MockMvcBuilders.standaloneSetup(new FailingController())
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
  }

  @Test
  void businessRuleBecomes422WithStableCode() throws Exception {
    mvc.perform(get("/business"))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.code").value("INSUFFICIENT_STOCK"))
        .andExpect(jsonPath("$.detail").value("Saldo insuficiente."))
        .andExpect(jsonPath("$.type").value("https://ribeira.app/problems/insufficient-stock"));
  }

  @Test
  void notFoundBecomes404() throws Exception {
    mvc.perform(get("/missing"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value(ResourceNotFoundException.CODE));
  }

  @Test
  void beanValidationListsFieldErrors() throws Exception {
    mvc.perform(
            post("/validated").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value(GlobalExceptionHandler.VALIDATION_FAILED))
        .andExpect(jsonPath("$.errors[0].field").value("name"));
  }

  @Test
  void malformedJsonGetsProblemDetailWithCode() throws Exception {
    mvc.perform(post("/validated").contentType(MediaType.APPLICATION_JSON).content("{oops"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
  }

  @Test
  void unexpectedErrorsDoNotLeakDetails() throws Exception {
    mvc.perform(get("/boom"))
        .andExpect(status().isInternalServerError())
        .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
        .andExpect(
            jsonPath("$.detail")
                .value("Erro interno. Informe o identificador de correlação ao suporte."));
  }

  record NamedRequest(@NotBlank String name) {}

  @RestController
  static class FailingController {

    @GetMapping("/business")
    void business() {
      throw new BusinessRuleException("INSUFFICIENT_STOCK", "Saldo insuficiente.");
    }

    @GetMapping("/missing")
    void missing() {
      throw new ResourceNotFoundException("Lote", UUID.randomUUID());
    }

    @PostMapping("/validated")
    void validated(@Valid @RequestBody NamedRequest request) {}

    @GetMapping("/boom")
    void boom() {
      throw new IllegalStateException("secret internal detail");
    }
  }
}
