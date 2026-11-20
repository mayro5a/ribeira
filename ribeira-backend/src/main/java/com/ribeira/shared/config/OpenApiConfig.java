package com.ribeira.shared.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

  private static final String BEARER = "bearer";

  @Bean
  OpenAPI ribeiraOpenApi() {
    return new OpenAPI()
        .info(
            new Info()
                .title("Ribeira API")
                .version("v1")
                .description(
                    "API de gestão de doações, estoque, distribuição e perdas de alimentos para"
                        + " comunidades ribeirinhas. Erros seguem RFC 9457 (Problem Details) com"
                        + " os campos adicionais `code` e `correlationId`."))
        .components(
            new Components()
                .addSecuritySchemes(
                    BEARER,
                    new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")))
        .addSecurityItem(new SecurityRequirement().addList(BEARER));
  }
}
