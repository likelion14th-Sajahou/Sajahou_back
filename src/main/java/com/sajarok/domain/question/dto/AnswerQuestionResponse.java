package com.sajarok.domain.question.dto;

import com.sajarok.domain.question.entity.AiQuestion;
import com.sajarok.domain.question.entity.QuestionStatus;

public record AnswerQuestionResponse(
    Long questionId, int seq, QuestionStatus status, String answer) {

  public static AnswerQuestionResponse from(AiQuestion q) {
    return new AnswerQuestionResponse(q.getId(), q.getSeq(), q.getStatus(), q.getAnswer());
  }
}
