package com.sajarok.domain.project.dto;

import com.sajarok.domain.question.dto.QuestionView;
import com.sajarok.domain.question.entity.QuestionStatus;
import java.util.List;

/** 32번 마치기 질문 응답. quote는 빈 곳 이유(H-17), answer는 진행 중이던 답(200일 때). */
public record FinishQuestionsResponse(List<Item> questions) {

  public record Item(
      Long questionId,
      int seq,
      String tag,
      String quote,
      String content,
      String answer,
      QuestionStatus status) {}

  public static FinishQuestionsResponse from(List<QuestionView> views) {
    return new FinishQuestionsResponse(
        views.stream()
            .map(
                v ->
                    new Item(
                        v.questionId(),
                        v.seq(),
                        v.tag(),
                        v.quote(),
                        v.content(),
                        v.answer(),
                        v.status()))
            .toList());
  }
}
