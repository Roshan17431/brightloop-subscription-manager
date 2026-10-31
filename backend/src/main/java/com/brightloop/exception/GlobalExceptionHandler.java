package com.brightloop.exception;

import jakarta.servlet.http.HttpServletRequest;
import java.util.*;
import org.slf4j.*;
import org.springframework.dao.*;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class GlobalExceptionHandler {
  private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

  private ResponseEntity<ApiError> error(
      HttpStatus status,
      String code,
      String message,
      HttpServletRequest req,
      Map<String, String> details) {
    return ResponseEntity.status(status)
        .body(ApiError.of(status, code, message, req.getRequestURI(), details));
  }

  @ExceptionHandler(ApiException.class)
  ResponseEntity<ApiError> handle(ApiException e, HttpServletRequest req) {
    return error(e.status, e.code, e.getMessage(), req, null);
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  ResponseEntity<ApiError> validation(MethodArgumentNotValidException e, HttpServletRequest req) {
    var details = new LinkedHashMap<String, String>();
    e.getBindingResult()
        .getFieldErrors()
        .forEach(v -> details.put(v.getField(), v.getDefaultMessage()));
    return error(
        HttpStatus.BAD_REQUEST,
        "VALIDATION_ERROR",
        "Please correct the highlighted fields",
        req,
        details);
  }

  @ExceptionHandler({
    IllegalArgumentException.class,
    MethodArgumentTypeMismatchException.class,
    HttpMessageNotReadableException.class
  })
  ResponseEntity<ApiError> invalid(Exception e, HttpServletRequest req) {
    return error(
        HttpStatus.BAD_REQUEST,
        "INVALID_REQUEST",
        e instanceof IllegalArgumentException
            ? e.getMessage()
            : "Malformed request or invalid field value",
        req,
        null);
  }

  @ExceptionHandler(AuthenticationException.class)
  ResponseEntity<ApiError> authentication(Exception e, HttpServletRequest req) {
    return error(
        HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "Invalid email or password", req, null);
  }

  @ExceptionHandler(AccessDeniedException.class)
  ResponseEntity<ApiError> access(Exception e, HttpServletRequest req) {
    return error(HttpStatus.FORBIDDEN, "FORBIDDEN", "Access denied", req, null);
  }

  @ExceptionHandler({
    DataIntegrityViolationException.class,
    OptimisticLockingFailureException.class
  })
  ResponseEntity<ApiError> conflict(Exception e, HttpServletRequest req) {
    return error(
        HttpStatus.CONFLICT,
        "CONFLICT",
        "The record conflicts with existing data or was changed by another request",
        req,
        null);
  }

  @ExceptionHandler(Exception.class)
  ResponseEntity<ApiError> unexpected(Exception e, HttpServletRequest req) {
    if (e instanceof org.springframework.web.ErrorResponse framework) {
      var status = HttpStatus.resolve(framework.getStatusCode().value());
      if (status != null) {
        return ResponseEntity.status(status)
            .headers(framework.getHeaders())
            .body(
                ApiError.of(
                    status, status.name(), status.getReasonPhrase(), req.getRequestURI(), null));
      }
    }
    log.error("Unexpected request failure at {}", req.getRequestURI(), e);
    return error(
        HttpStatus.INTERNAL_SERVER_ERROR,
        "INTERNAL_ERROR",
        "An unexpected error occurred",
        req,
        null);
  }
}
