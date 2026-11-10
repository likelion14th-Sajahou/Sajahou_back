package com.sajarok.domain.question.dto;

import com.sajarok.domain.question.entity.QuestionStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public record AnswerQuestionRequest(
    @NotNull(message = "답할지 건너뛸지 골라 주세요.") QuestionStatus status,
    @Size(max = 10000, message = "답변은 최대 10,000자까지 가능해요.") String answer,
    List<Long> attachmentIds) {}
