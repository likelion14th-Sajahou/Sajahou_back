package com.sajarok.domain.project.dto;

import com.sajarok.domain.question.dto.QuestionView;
import java.util.List;

/** 30번 응답. insufficientMaterial이면 questions가 비고 FE가 '자료가 부족해요' + 5칸 직접 채우기를 보여 준다. */
public record RecordQuestionsResponse(boolean insufficientMaterial, List<QuestionView> questions) {

  public static RecordQuestionsResponse of(List<QuestionView> questions) {
    return new RecordQuestionsResponse(false, questions);
  }

  public static RecordQuestionsResponse insufficient() {
    return new RecordQuestionsResponse(true, List.of());
  }
}
