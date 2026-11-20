package com.ribeira.shared.security;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.nimbusds.jose.proc.SecurityContext;
import com.ribeira.shared.config.SecurityProperties;
import com.ribeira.shared.web.ProblemResponseWriter;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Stateless API security: short-lived JWT access tokens in the Authorization header (no cookies,
 * hence no CSRF exposure) and a refresh cookie scoped to the auth endpoints (ADR-0005).
 */
@Configuration
public class SecurityConfig {

  public static final String ISSUER = "ribeira";

  private static final String[] PUBLIC_ENDPOINTS = {
    "/api/v1/auth/login",
    "/api/v1/auth/refresh",
    "/api/v1/auth/logout",
    "/actuator/health/**",
    "/actuator/prometheus",
    "/v3/api-docs/**",
    "/swagger-ui/**",
    "/swagger-ui.html"
  };

  @Bean
  SecurityFilterChain securityFilterChain(HttpSecurity http, ProblemResponseWriter problems)
      throws Exception {
    AuthenticationEntryPoint unauthenticated =
        (request, response, ex) ->
            problems.write(
                response,
                HttpStatus.UNAUTHORIZED,
                "UNAUTHENTICATED",
                "Sessão ausente ou expirada. Faça login novamente.");
    http.csrf(csrf -> csrf.disable())
        .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .cors(cors -> {})
        .headers(
            headers ->
                headers.referrerPolicy(
                    r -> r.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.SAME_ORIGIN)))
        .authorizeHttpRequests(
            auth ->
                auth.requestMatchers(HttpMethod.OPTIONS, "/**")
                    .permitAll()
                    .requestMatchers(PUBLIC_ENDPOINTS)
                    .permitAll()
                    .requestMatchers("/api/**")
                    .authenticated()
                    .anyRequest()
                    .denyAll())
        .oauth2ResourceServer(
            oauth ->
                oauth
                    .jwt(jwt -> {})
                    .authenticationEntryPoint(unauthenticated)
                    .accessDeniedHandler(
                        (request, response, ex) ->
                            problems.write(
                                response,
                                HttpStatus.FORBIDDEN,
                                "ACCESS_DENIED",
                                "Acesso negado a este recurso.")))
        .exceptionHandling(ex -> ex.authenticationEntryPoint(unauthenticated));
    return http.build();
  }

  @Bean
  SecretKey jwtSigningKey(SecurityProperties properties) {
    return new SecretKeySpec(properties.jwtSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256");
  }

  /** Expiry is checked against the application clock, the same one used to issue tokens. */
  @Bean
  JwtDecoder jwtDecoder(SecretKey jwtSigningKey, Clock clock) {
    NimbusJwtDecoder decoder =
        NimbusJwtDecoder.withSecretKey(jwtSigningKey).macAlgorithm(MacAlgorithm.HS256).build();
    JwtTimestampValidator timestamps = new JwtTimestampValidator(Duration.ofSeconds(30));
    timestamps.setClock(clock);
    decoder.setJwtValidator(
        new DelegatingOAuth2TokenValidator<>(timestamps, new JwtIssuerValidator(ISSUER)));
    return decoder;
  }

  @Bean
  JwtEncoder jwtEncoder(SecretKey jwtSigningKey) {
    return new NimbusJwtEncoder(new ImmutableSecret<SecurityContext>(jwtSigningKey));
  }

  @Bean
  PasswordEncoder passwordEncoder() {
    return PasswordEncoderFactories.createDelegatingPasswordEncoder();
  }

  @Bean
  CorsConfigurationSource corsConfigurationSource(SecurityProperties properties) {
    CorsConfiguration config = new CorsConfiguration();
    config.setAllowedOrigins(properties.corsAllowedOrigins());
    config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
    config.setAllowedHeaders(
        List.of(
            "Authorization",
            "Content-Type",
            "X-Requested-With",
            "X-Device-Id",
            "X-Correlation-Id"));
    config.setExposedHeaders(List.of("X-Correlation-Id"));
    config.setAllowCredentials(true);
    config.setMaxAge(3600L);
    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/api/**", config);
    return source;
  }
}
