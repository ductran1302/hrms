package com.hrms.common.web;

/** Trang dữ liệu chuẩn cho mọi list API phân trang. */
public record PageResponse<T>(java.util.List<T> items, int page, int size, long total) {

  public static <T> PageResponse<T> of(java.util.List<T> items, int page, int size, long total) {
    return new PageResponse<>(items, page, size, total);
  }
}
