package com.sajarok.global.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import java.util.Map;

public record ApiResponse<T>(boolean success, T data, ErrorBody error) {

  public static <T> ApiResponse<T> ok(T data) {
    return new ApiResponse<>(true, data, null);
  }

  public static ApiResponse<Void> fail(String code, String message, List<FieldError> fields) {
    return fail(code, message, fields, null);
  }

  public static ApiResponse<Void> fail(
      String code, String message, List<FieldError> fields, Map<String, Object> detail) {
    return new ApiResponse<>(false, null, new ErrorBody(code, message, fields, detail));
  }

  /** detail은 에러별 추가 정보(예: 409 MATERIAL_CHANGED의 answeredCount). 없으면 JSON에서 빠진다. */
  public record ErrorBody(
      String code,
      String message,
      List<FieldError> fields,
      @JsonInclude(JsonInclude.Include.NON_NULL) Map<String, Object> detail) {}

  public record FieldError(String field, String reason) {}
}
