package com.ribeira.shared.exception;

import com.ribeira.shared.web.CorrelationIdFilter;
import java.net.URI;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;

/** Builds RFC 9457 problem details with the fields every Ribeira error carries. */
public final class ProblemDetails {

  public static final String CODE = "code";
  public static final String CORRELATION_ID = "correlationId";
  private static final String TYPE_PREFIX = "https://ribeira.app/problems/";

  private ProblemDetails() {}

  public static ProblemDetail of(HttpStatusCode status, String code, String detail) {
    ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
    return enrich(problem, code);
  }

  /** Adds {@code code} and {@code correlationId} when missing. */
  public static ProblemDetail enrich(ProblemDetail problem, String code) {
    if (problem.getProperties() == null || !problem.getProperties().containsKey(CODE)) {
      problem.setProperty(CODE, code);
    }
    String resolvedCode = String.valueOf(problem.getProperties().get(CODE));
    problem.setType(URI.create(TYPE_PREFIX + resolvedCode.toLowerCase().replace('_', '-')));
    problem.setProperty(CORRELATION_ID, CorrelationIdFilter.current());
    return problem;
  }

  public static String defaultCode(HttpStatusCode status) {
    return switch (status.value()) {
      case 400 -> "BAD_REQUEST";
      case 401 -> "UNAUTHENTICATED";
      case 403 -> ForbiddenException.CODE;
      case 404 -> ResourceNotFoundException.CODE;
      case 405 -> "METHOD_NOT_ALLOWED";
      case 409 -> "CONFLICT";
      case 415 -> "UNSUPPORTED_MEDIA_TYPE";
      case 429 -> "TOO_MANY_REQUESTS";
      default -> status.is5xxServerError() ? "INTERNAL_ERROR" : "REQUEST_ERROR";
    };
  }
}
