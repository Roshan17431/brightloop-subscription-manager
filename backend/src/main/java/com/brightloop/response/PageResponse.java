package com.brightloop.response;

import java.util.List;
import java.util.function.Function;
import org.springframework.data.domain.*;

public record PageResponse<T>(
    List<T> content, long totalElements, int totalPages, int number, int size) {
  public static Pageable request(int page, int size) {
    if (page < 0 || size < 1 || size > 100)
      throw new IllegalArgumentException(
          "Page must be nonnegative; size must be between 1 and 100");
    return PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "id"));
  }

  public static <E, T> PageResponse<T> from(Page<E> page, Function<E, T> map) {
    return new PageResponse<>(
        page.getContent().stream().map(map).toList(),
        page.getTotalElements(),
        page.getTotalPages(),
        page.getNumber(),
        page.getSize());
  }

  public static String search(String q) {
    if (q == null) return "";
    if (q.length() > 200) throw new IllegalArgumentException("Search is too long");
    return q.trim()
        .toLowerCase(java.util.Locale.ROOT)
        .replace("\\", "\\\\")
        .replace("%", "\\%")
        .replace("_", "\\_");
  }
}
