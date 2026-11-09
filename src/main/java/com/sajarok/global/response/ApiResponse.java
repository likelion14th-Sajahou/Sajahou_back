package com.sajarok.global.response;

import java.util.List;

public record ApiResponse<T>(boolean success, T data, ErrorBody error) {

  public static <T> ApiResponse<T> ok(T data) {
    return new ApiResponse<>(true, data, null);
  }

  public static ApiResponse<Void> fail(String code, String message, List<FieldError> fields) {
    return new ApiResponse<>(false, null, new ErrorBody(code, message, fields));
  }

  public record ErrorBody(String code, String message, List<FieldError> fields) {}

  public record FieldError(String field, String reason) {}
}
