package com.sajarok.global.exception;

import com.sajarok.global.response.ApiResponse;
import com.sajarok.global.response.ApiResponse.FieldError;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

  /** 서비스에서 던진 규칙 위반 (404, 409 등) */
  @ExceptionHandler(BusinessException.class)
  public ResponseEntity<ApiResponse<Void>> handleBusiness(BusinessException e) {
    return respond(e.getErrorCode(), e.getMessage(), null);
  }

  /**
   * @Valid 검사 실패. 칸마다 이유를 fields에 모두 담아 한 번에 돌려준다 (S-05)
   */
  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException e) {
    List<FieldError> fields =
        e.getBindingResult().getFieldErrors().stream()
            .map(fe -> new FieldError(fe.getField(), fe.getDefaultMessage()))
            .toList();
    ErrorCode code = ErrorCode.VALIDATION_ERROR;
    return respond(code, code.getMessage(), fields);
  }

  /** 요청 본문이 JSON이 아니거나, 경로의 ID가 숫자가 아닐 때 */
  @ExceptionHandler({
    HttpMessageNotReadableException.class,
    MethodArgumentTypeMismatchException.class
  })
  public ResponseEntity<ApiResponse<Void>> handleBadRequest(Exception e) {
    ErrorCode code = ErrorCode.VALIDATION_ERROR;
    return respond(code, code.getMessage(), null);
  }

  /** 없는 URL */
  @ExceptionHandler(NoResourceFoundException.class)
  public ResponseEntity<ApiResponse<Void>> handleNoResource(NoResourceFoundException e) {
    ErrorCode code = ErrorCode.NOT_FOUND;
    return respond(code, code.getMessage(), null);
  }

  /** 그 밖의 모든 예외 */
  @ExceptionHandler(Exception.class)
  public ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception e) {
    log.error("처리하지 못한 예외", e);
    ErrorCode code = ErrorCode.INTERNAL_ERROR;
    return respond(code, code.getMessage(), null);
  }

  private ResponseEntity<ApiResponse<Void>> respond(
      ErrorCode code, String message, List<FieldError> fields) {
    return ResponseEntity.status(code.getStatus())
        .body(ApiResponse.fail(code.name(), message, fields));
  }
}
