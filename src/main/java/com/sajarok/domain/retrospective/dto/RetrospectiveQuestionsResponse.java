package com.sajarok.domain.retrospective.dto;

import com.sajarok.domain.question.dto.QuestionView;
import java.util.List;

/** 17번 응답. insufficientMaterial이면 questions가 비고 FE가 '자료를 더 올려 주세요'를 보여 준다. */
public record RetrospectiveQuestionsResponse(
    boolean insufficientMaterial, List<QuestionView> questions) {

  public static RetrospectiveQuestionsResponse of(List<QuestionView> questions) {
    return new RetrospectiveQuestionsResponse(false, questions);
  }

  public static RetrospectiveQuestionsResponse insufficient() {
    return new RetrospectiveQuestionsResponse(true, List.of());
  }
}
