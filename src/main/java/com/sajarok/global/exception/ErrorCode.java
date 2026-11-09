package com.sajarok.global.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {
  VALIDATION_ERROR(HttpStatus.BAD_REQUEST, "입력값을 확인해 주세요."),
  NOT_FOUND(HttpStatus.NOT_FOUND, "찾을 수 없어요."),
  INVALID_STATE(HttpStatus.CONFLICT, "지금은 할 수 없는 동작이에요."),
  EMPTY_MATERIAL(HttpStatus.BAD_REQUEST, "자료를 하나 이상 넣어 주세요."),
  COMPETENCY_LIMIT(HttpStatus.BAD_REQUEST, "역량 태그는 1~3개 골라 주세요."),
  UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "다시 로그인해 주세요."),
  MATERIAL_CHANGED(HttpStatus.CONFLICT, "질문을 만든 뒤 자료가 바뀌었어요."),
  DEADLINE_PASSED(HttpStatus.CONFLICT, "작성 기간이 지났어요."),
  AI_IN_PROGRESS(HttpStatus.CONFLICT, "AI가 아직 생각하는 중이에요. 잠시 뒤 다시 시도해 주세요."),
  ATTACHMENT_PENDING(HttpStatus.CONFLICT, "첨부 파일을 아직 읽는 중이에요. 잠시 뒤 다시 시도해 주세요."),
  FILE_TOO_LARGE(HttpStatus.PAYLOAD_TOO_LARGE, "파일은 20MB까지 올릴 수 있어요."),
  UNSUPPORTED_FILE(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "지원하지 않는 파일 형식이에요."),
  INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "잠시 후 다시 시도해 주세요."),
  AI_FAILED(HttpStatus.BAD_GATEWAY, "AI 응답을 받지 못했어요. 다시 시도해 주세요."),
  AI_TIMEOUT(HttpStatus.GATEWAY_TIMEOUT, "AI 응답이 지연되는 중이에요. 다시 시도해 주세요.");

  private final HttpStatus status;
  private final String message;
}
