package com.ribeira.shared.exception;

import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.Nullable;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

  public static final String VALIDATION_FAILED = "VALIDATION_FAILED";
  private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

  @ExceptionHandler(ApiException.class)
  ResponseEntity<ProblemDetail> handleApiException(ApiException ex) {
    if (ex.status().is5xxServerError()) {
      log.error("Application error code={}", ex.code(), ex);
    } else {
      log.info("Request refused code={} status={}", ex.code(), ex.status().value());
    }
    return ResponseEntity.status(ex.status())
        .body(ProblemDetails.of(ex.status(), ex.code(), ex.getMessage()));
  }

  @ExceptionHandler(AccessDeniedException.class)
  ResponseEntity<ProblemDetail> handleAccessDenied(AccessDeniedException ex) {
    return ResponseEntity.status(HttpStatus.FORBIDDEN)
        .body(
            ProblemDetails.of(
                HttpStatus.FORBIDDEN, ForbiddenException.CODE, "Acesso negado a este recurso."));
  }

  @ExceptionHandler(AuthenticationException.class)
  ResponseEntity<ProblemDetail> handleAuthentication(AuthenticationException ex) {
    return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
        .body(
            ProblemDetails.of(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "Autenticação exigida."));
  }

  @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
  ResponseEntity<ProblemDetail> handleOptimisticLock(ObjectOptimisticLockingFailureException ex) {
    return ResponseEntity.status(HttpStatus.CONFLICT)
        .body(
            ProblemDetails.of(
                HttpStatus.CONFLICT,
                "CONCURRENT_MODIFICATION",
                "O registro foi alterado por outra pessoa. Recarregue e tente novamente."));
  }

  /**
   * Constraint violations should normally be prevented by domain validation; reaching this handler
   * means a race or a missing check, so it is logged as a warning for investigation.
   */
  @ExceptionHandler(DataIntegrityViolationException.class)
  ResponseEntity<ProblemDetail> handleDataIntegrity(DataIntegrityViolationException ex) {
    log.warn("Data integrity violation: {}", ex.getMostSpecificCause().getClass().getSimpleName());
    return ResponseEntity.status(HttpStatus.CONFLICT)
        .body(
            ProblemDetails.of(
                HttpStatus.CONFLICT,
                "DATA_INTEGRITY_VIOLATION",
                "A operação viola uma restrição de integridade dos dados."));
  }

  @ExceptionHandler(Exception.class)
  ResponseEntity<ProblemDetail> handleUnexpected(Exception ex) {
    log.error("Unexpected error", ex);
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
        .body(
            ProblemDetails.of(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "INTERNAL_ERROR",
                "Erro interno. Informe o identificador de correlação ao suporte."));
  }

  @Override
  protected ResponseEntity<Object> handleMethodArgumentNotValid(
      MethodArgumentNotValidException ex,
      HttpHeaders headers,
      HttpStatusCode status,
      WebRequest request) {
    List<Map<String, String>> errors =
        ex.getBindingResult().getFieldErrors().stream().map(this::toFieldError).toList();
    return validationProblem(errors);
  }

  @Override
  protected ResponseEntity<Object> handleHandlerMethodValidationException(
      HandlerMethodValidationException ex,
      HttpHeaders headers,
      HttpStatusCode status,
      WebRequest request) {
    List<Map<String, String>> errors =
        ex.getAllErrors().stream()
            .map(
                error ->
                    Map.of(
                        "field",
                        error instanceof FieldError fe ? fe.getField() : "parameter",
                        "message",
                        String.valueOf(error.getDefaultMessage())))
            .toList();
    return validationProblem(errors);
  }

  @Override
  protected ResponseEntity<Object> handleExceptionInternal(
      Exception ex,
      @Nullable Object body,
      HttpHeaders headers,
      HttpStatusCode statusCode,
      WebRequest request) {
    Object enriched = body;
    if (body instanceof ProblemDetail problem) {
      enriched = ProblemDetails.enrich(problem, ProblemDetails.defaultCode(statusCode));
    } else if (body == null) {
      enriched =
          ProblemDetails.of(
              statusCode, ProblemDetails.defaultCode(statusCode), "Requisição inválida.");
    }
    return super.handleExceptionInternal(ex, enriched, headers, statusCode, request);
  }

  private ResponseEntity<Object> validationProblem(List<Map<String, String>> errors) {
    ProblemDetail problem =
        ProblemDetails.of(
            HttpStatus.BAD_REQUEST, VALIDATION_FAILED, "Um ou mais campos são inválidos.");
    problem.setProperty("errors", errors);
    return ResponseEntity.badRequest().body(problem);
  }

  private Map<String, String> toFieldError(FieldError error) {
    return Map.of("field", error.getField(), "message", String.valueOf(error.getDefaultMessage()));
  }
}
