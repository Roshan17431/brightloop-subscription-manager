package com.brightloop.response;

import java.time.Instant;
import org.springframework.http.*;

public record ApiResponse<T>(
    boolean success, int statusCode, String status, String message, T data, Instant timestamp) {
  public static <T> ResponseEntity<ApiResponse<T>> ok(T data) {
    return of(HttpStatus.OK, "Success", data);
  }

  public static <T> ResponseEntity<ApiResponse<T>> created(T data) {
    return of(HttpStatus.CREATED, "Created", data);
  }

  public static <T> ResponseEntity<ApiResponse<T>> of(HttpStatus status, String message, T data) {
    return ResponseEntity.status(status)
        .body(new ApiResponse<>(true, status.value(), status.name(), message, data, Instant.now()));
  }
}
