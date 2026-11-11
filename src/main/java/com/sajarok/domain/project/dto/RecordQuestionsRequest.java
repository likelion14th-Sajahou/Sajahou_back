package com.sajarok.domain.project.dto;

import com.sajarok.domain.project.entity.RecordQuestionMode;

/** 30번 요청. mode가 없으면(본문 없음 포함) AUTO. */
public record RecordQuestionsRequest(RecordQuestionMode mode) {}
