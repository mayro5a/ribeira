package com.ribeira.shared.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ribeira.shared.exception.ProblemDetails;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

/** Writes Problem Details from servlet filters, where controller advice does not apply. */
@Component
public class ProblemResponseWriter {

  private final ObjectMapper objectMapper;

  public ProblemResponseWriter(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  public void write(HttpServletResponse response, HttpStatus status, String code, String detail)
      throws IOException {
    response.setStatus(status.value());
    response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
    response.setCharacterEncoding("UTF-8");
    objectMapper.writeValue(response.getOutputStream(), ProblemDetails.of(status, code, detail));
  }
}
