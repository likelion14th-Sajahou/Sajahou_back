package com.sajarok.domain.matching.dto;

import jakarta.validation.constraints.Size;

/**
 * 38번 고친 답 저장 요청. editedAnswer가 null이면 AI 초안으로 되돌린다.
 *
 * <p>최대 길이는 TEXT 컬럼을 넘겨 DB 오류(500)가 나지 않게 막는 용도다. 문항의 charLimit 초과는 막지 않는다.
 */
public record EditedAnswerRequest(
    @Size(max = 10000, message = "답은 10,000자까지 쓸 수 있어요.") String editedAnswer) {}
