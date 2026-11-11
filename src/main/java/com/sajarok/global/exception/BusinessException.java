package com.sajarok.global.exception;

import java.util.Map;
import lombok.Getter;

@Getter
public class BusinessException extends RuntimeException {

  private final ErrorCode errorCode;
  private final Map<String, Object> detail;

  public BusinessException(ErrorCode errorCode) {
    this(errorCode, errorCode.getMessage());
  }

  public BusinessException(ErrorCode errorCode, String message) {
    this(errorCode, message, null);
  }

  /** detail은 응답 error.detail로 나간다 (예: 409 MATERIAL_CHANGED의 answeredCount). */
  public BusinessException(ErrorCode errorCode, String message, Map<String, Object> detail) {
    super(message);
    this.errorCode = errorCode;
    this.detail = detail;
  }
}
