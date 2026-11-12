package com.sajarok.domain.retrospective.dto;

/** 17번 요청. mode가 없으면(본문 없음 포함) AUTO. */
public record RetrospectiveQuestionsRequest(RetroQuestionMode mode) {}
