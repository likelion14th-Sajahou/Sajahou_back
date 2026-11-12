package com.sajarok.domain.retrospective.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sajarok.domain.question.dto.NewQuestion;
import com.sajarok.domain.retrospective.ai.RetroQuestionResult.Question;
import com.sajarok.domain.retrospective.ai.RetroQuestionResult.Validated;
import com.sajarok.global.exception.BusinessException;
import com.sajarok.global.exception.ErrorCode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 17번 AI 응답 검증: 쓸 수 있는 질문은 살리고, 하나도 없을 때만 AI_FAILED. */
class RetroQuestionResultTest {

  private static final String MATERIAL = "게시글 목록 API에서 N+1 문제를 fetch join으로 해결";
  private static final Map<Long, String> ATTACHMENTS = Map.of(502L, "지연 로딩과 N+1 문제, fetch join");

  private static Validated validate(Boolean insufficient, List<Question> questions) {
    return new RetroQuestionResult(insufficient, questions).validate(MATERIAL, ATTACHMENTS);
  }

  @Test
  @DisplayName("원문에 있는 quote는 남기고, 없는 quote는 null로 바꾸되 질문은 살린다")
  void keepsQuestionsAndVerifiesQuotes() {
    Validated v =
        validate(
            false,
            List.of(
                new Question("근거와 숫자", MATERIAL, null, " 쿼리 수가 얼마나 줄었나요? "),
                new Question("세션과 연결", "지어낸 문장", 502L, "세션 내용과 어떻게 이어지나요?")));

    assertThat(v.insufficientMaterial()).isFalse();
    assertThat(v.questions())
        .containsExactly(
            new NewQuestion("근거와 숫자", MATERIAL, null, "쿼리 수가 얼마나 줄었나요?"),
            new NewQuestion("세션과 연결", null, null, "세션 내용과 어떻게 이어지나요?"));
  }

  @Test
  @DisplayName("태그가 띄어쓰기·가운뎃점만 다르면 목록의 태그로 맞춘다")
  void normalizesTag() {
    Validated v = validate(false, List.of(new Question("근거와숫자", null, null, "숫자가 있나요?")));

    assertThat(v.questions().get(0).tag()).isEqualTo("근거와 숫자");
  }

  @Test
  @DisplayName("목록에 없는 태그·빈 질문은 그 질문만 버리고, 넘겨주지 않은 첨부 번호는 출처만 지운다")
  void dropsOnlyBadQuestions() {
    Validated v =
        validate(
            false,
            List.of(
                new Question("엉뚱한 태그", null, null, "버려질 질문"),
                new Question("빠진 칸", null, null, "  "),
                new Question("느낀 점", MATERIAL, 999L, "무엇을 배웠나요?")));

    assertThat(v.questions()).containsExactly(new NewQuestion("느낀 점", null, null, "무엇을 배웠나요?"));
  }

  @Test
  @DisplayName("질문이 5개를 넘으면 앞에서 5개만 쓴다 (전에는 전체 실패)")
  void limitsToFive() {
    List<Question> seven = new ArrayList<>();
    for (int i = 1; i <= 7; i++) {
      seven.add(new Question("빠진 칸", null, null, "질문 " + i));
    }

    assertThat(validate(false, seven).questions()).hasSize(5);
  }

  @Test
  @DisplayName("자료 부족이라고 하면 질문이 같이 와도 부족으로 본다")
  void insufficientWins() {
    Validated v = validate(true, List.of(new Question("빠진 칸", null, null, "질문")));

    assertThat(v.insufficientMaterial()).isTrue();
    assertThat(v.questions()).isEmpty();
  }

  @Test
  @DisplayName("쓸 수 있는 질문이 하나도 없으면 AI_FAILED")
  void nothingUsable_aiFailed() {
    assertThatThrownBy(() -> validate(false, List.of(new Question("엉뚱한 태그", null, null, "질문"))))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.AI_FAILED);
    assertThatThrownBy(() -> validate(false, null))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.AI_FAILED);
  }
}
