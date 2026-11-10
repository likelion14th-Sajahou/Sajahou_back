package com.sajarok.domain.matching.dto;

/** 38번 고친 답 저장 응답. charCount는 공백·줄바꿈 포함, 이모지 1글자 기준. */
public record EditedAnswerResponse(Long questionId, String editedAnswer, int charCount) {}
