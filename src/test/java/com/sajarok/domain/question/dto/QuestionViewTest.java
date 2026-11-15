package com.sajarok.domain.question.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.sajarok.domain.question.entity.AiQuestion;
import com.sajarok.domain.question.entity.QuestionOwner;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 질문 응답 모양: quote가 없는 질문은 "전체"로 내보낸다. */
class QuestionViewTest {

  @Test
  @DisplayName("17·30번: quote가 없는 질문은 \"전체\"로 내보내고, 출처 첨부는 그대로 둔다 (DB에는 null 그대로)")
  void from_noQuote_wholeMaterial() {
    AiQuestion q =
        AiQuestion.create(
            QuestionOwner.retrospective(37L), 4, "느낀 점", null, 62L, "이번 세션에서 무엇을 느꼈나요?");

    QuestionView view = QuestionView.from(q);

    assertThat(view.quote()).isEqualTo("전체");
    assertThat(view.sourceAttachmentId()).isEqualTo(62L);
    assertThat(q.getQuote()).isNull();
  }

  @Test
  @DisplayName("17·30번: 첨부도 모르면 quote는 \"전체\", 출처는 null")
  void from_noQuoteNoSource() {
    AiQuestion q =
        AiQuestion.create(QuestionOwner.projectRecord(77L), 1, "근거와 숫자", null, null, "결과가 있었나요?");

    QuestionView view = QuestionView.from(q);

    assertThat(view.quote()).isEqualTo(QuestionView.WHOLE_MATERIAL);
    assertThat(view.sourceAttachmentId()).isNull();
  }

  @Test
  @DisplayName("원문 문장이 있는 quote는 그대로 내보낸다")
  void from_realQuote_unchanged() {
    AiQuestion q =
        AiQuestion.create(
            QuestionOwner.retrospective(37L),
            1,
            "근거와 숫자",
            "6명 중 4명이 칸을 비운 채 저장했다.",
            62L,
            "4명이 비운 칸은 주로 어느 칸이었나요?");

    assertThat(QuestionView.from(q).quote()).isEqualTo("6명 중 4명이 칸을 비운 채 저장했다.");
  }
}
