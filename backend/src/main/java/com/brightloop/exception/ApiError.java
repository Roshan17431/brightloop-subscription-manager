package com.brightloop.exception;

import java.time.Instant;
import java.util.Map;
import org.springframework.http.HttpStatus;

public record ApiError(
    boolean success,
    int statusCode,
    String status,
    String message,
    String errorCode,
    String path,
    Instant timestamp,
    Map<String, String> details) {
  public static ApiError of(
      HttpStatus status, String code, String message, String path, Map<String, String> details) {
    return new ApiError(
        false,
        status.value(),
        status.name(),
        message,
        code,
        path,
        Instant.now(),
        details == null ? Map.of() : details);
  }
}
