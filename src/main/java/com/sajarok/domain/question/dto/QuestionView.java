package com.sajarok.domain.question.dto;

import com.sajarok.domain.question.entity.AiQuestion;
import com.sajarok.domain.question.entity.QuestionStatus;

public record QuestionView(
    Long questionId,
    int seq,
    String tag,
    String quote,
    Long sourceAttachmentId,
    String content,
    String answer,
    QuestionStatus status) {

  public static QuestionView from(AiQuestion q) {
    return new QuestionView(
        q.getId(),
        q.getSeq(),
        q.getTag(),
        q.getQuote(),
        q.getSourceAttachmentId(),
        q.getContent(),
        q.getAnswer(),
        q.getStatus());
  }
}
