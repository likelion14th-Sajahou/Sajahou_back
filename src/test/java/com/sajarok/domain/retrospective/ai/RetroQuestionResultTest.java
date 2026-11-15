package com.sajarok.domain.retrospective.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.groups.Tuple.tuple;

import com.sajarok.domain.question.dto.NewQuestion;
import com.sajarok.domain.retrospective.ai.RetroQuestionResult.Question;
import com.sajarok.domain.retrospective.ai.RetroQuestionResult.Validated;
import com.sajarok.domain.retrospective.entity.RetrospectiveType;
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
    return validate(insufficient, questions, RetrospectiveType.SESSION);
  }

  private static Validated validate(
      Boolean insufficient, List<Question> questions, RetrospectiveType type) {
    return new RetroQuestionResult(insufficient, questions).validate(MATERIAL, ATTACHMENTS, type);
  }

  @Test
  @DisplayName("원문에 있는 quote는 남기고, 없는 quote는 null로 바꾸되 질문은 살린다(글은 태그별 고정 질문으로)")
  void keepsQuestionsAndVerifiesQuotes() {
    Validated v =
        validate(
            false,
            List.of(
                new Question("근거와 숫자", MATERIAL, null, " 쿼리 수가 얼마나 줄었나요? "),
                new Question("세션과 연결", "지어낸 문장", 502L, "세션 내용과 어떻게 이어지나요?")));

    assertThat(v.insufficientMaterial()).isFalse();
    assertThat(v.questions())
        .startsWith(
            new NewQuestion("근거와 숫자", MATERIAL, null, "쿼리 수가 얼마나 줄었나요?"),
            new NewQuestion("세션과 연결", null, 502L, "이번 세션에서 배운 내용 중 실습이나 과제와 이어지는 부분이 있었나요?"));
  }

  @Test
  @DisplayName("QA 2: AI가 준 quote가 원문 대조에서 지워지면 \"이 말을 듣고~\" 질문 대신 태그별 고정 질문으로 바꾼다")
  void droppedQuote_usesFallbackQuestion() {
    Validated v =
        validate(
            false,
            List.of(new Question("역량 신호", "페이징이랑 fetch join 같이 쓰기", null, "이 말을 듣고 어떤 점을 바꿨나요?")));

    assertThat(v.questions())
        .startsWith(new NewQuestion("역량 신호", null, 502L, "이번 세션에서 직접 문제를 풀거나 팀과 함께 정한 일이 있었나요?"));
  }

  @Test
  @DisplayName("QA 2: quote를 처음부터 null로 준 질문은 (느낀 점이 아니면) AI 질문 그대로 둔다")
  void nullQuote_keepsQuestion() {
    Validated v =
        validate(false, List.of(new Question("빠진 칸", null, null, "과제 칸에 무엇을 했는지 적어 주세요.")));

    assertThat(v.questions().get(0).content()).isEqualTo("과제 칸에 무엇을 했는지 적어 주세요.");
  }

  @Test
  @DisplayName("QA 2: 느낀 점은 quote가 없으면(처음부터 null·지워짐) 세션 회고는 '이번 세션', 과제 회고는 '이번 과제' 고정 질문으로 바꾼다")
  void feeling_withoutQuote_usesFixedQuestionByType() {
    List<Question> feelings =
        List.of(
            new Question("느낀 점", null, null, "무엇을 배웠나요?"),
            new Question("느낀 점", "지어낸 문장", null, "이 부분에서 무엇을 느꼈나요?"));

    assertThat(validate(false, feelings, RetrospectiveType.SESSION).questions())
        .extracting(NewQuestion::content)
        .containsOnly("이번 세션에서 무엇을 느꼈나요?");
    assertThat(validate(false, feelings, RetrospectiveType.ASSIGNMENT).questions())
        .extracting(NewQuestion::content)
        .containsOnly("이번 과제에서 무엇을 느꼈나요?");
  }

  @Test
  @DisplayName("R-15: 느낀 점이 없으면 고정 느낀 점 질문(세션 회고는 '이번 세션')을 맨 끝에 붙인다")
  void noFeeling_appendsFixedFeelingLast() {
    Validated v = validate(false, List.of(new Question("빠진 칸", null, null, "질문 1")));

    assertThat(v.questions())
        .extracting(NewQuestion::tag, NewQuestion::content)
        .containsExactly(tuple("빠진 칸", "질문 1"), tuple("느낀 점", "이번 세션에서 무엇을 느꼈나요?"));
  }

  @Test
  @DisplayName("R-15: 느낀 점 없이 4개가 오면 느낀 점이 아닌 마지막 질문을 빼고 4개를 유지하며, 마지막은 느낀 점이다")
  void fourWithoutFeeling_keepsFourEndingWithFeeling() {
    List<Question> four = new ArrayList<>();
    for (int i = 1; i <= 4; i++) {
      four.add(new Question("빠진 칸", null, null, "질문 " + i));
    }

    assertThat(validate(false, four, RetrospectiveType.ASSIGNMENT).questions())
        .extracting(NewQuestion::content)
        .containsExactly("질문 1", "질문 2", "질문 3", "이번 과제에서 무엇을 느꼈나요?");
  }

  @Test
  @DisplayName("R-15: 느낀 점이 중간에 있으면 AI가 만든 그 질문을 맨 끝으로 옮긴다")
  void feelingInMiddle_movesToEnd() {
    Validated v =
        validate(
            false,
            List.of(
                new Question("근거와 숫자", MATERIAL, null, "쿼리 수가 얼마나 줄었나요?"),
                new Question("느낀 점", MATERIAL, null, "N+1을 고치며 새로 안 점이 있나요?"),
                new Question("빠진 칸", null, null, "과제 칸을 더 채워 주세요.")));

    assertThat(v.questions())
        .extracting(NewQuestion::tag, NewQuestion::content)
        .containsExactly(
            tuple("근거와 숫자", "쿼리 수가 얼마나 줄었나요?"),
            tuple("빠진 칸", "과제 칸을 더 채워 주세요."),
            tuple("느낀 점", "N+1을 고치며 새로 안 점이 있나요?"));
  }

  @Test
  @DisplayName("quote가 없는 질문은 AI가 읽은 첨부가 하나면 그 첨부를 출처로 두고, 첨부가 없거나 둘 이상이면 출처를 비운다")
  void noQuote_sourceIsOnlyAttachment() {
    RetroQuestionResult result =
        new RetroQuestionResult(
            false,
            List.of(
                new Question("빠진 칸", null, null, "과제 칸을 더 채워 주세요."),
                new Question("근거와 숫자", MATERIAL, null, "쿼리 수가 얼마나 줄었나요?")));
    RetrospectiveType session = RetrospectiveType.SESSION;

    assertThat(result.validate(MATERIAL, ATTACHMENTS, session).questions())
        .extracting(NewQuestion::tag, NewQuestion::quote, NewQuestion::sourceAttachmentId)
        .containsExactly(
            tuple("빠진 칸", null, 502L), tuple("근거와 숫자", MATERIAL, null), tuple("느낀 점", null, 502L));
    assertThat(result.validate(MATERIAL, Map.of(), session).questions())
        .extracting(NewQuestion::sourceAttachmentId)
        .containsOnlyNulls();
    assertThat(
            result
                .validate(MATERIAL, Map.of(502L, "지연 로딩", 503L, "fetch join"), session)
                .questions())
        .extracting(NewQuestion::sourceAttachmentId)
        .containsOnlyNulls();
  }

  @Test
  @DisplayName("QA 2: 태그 목록의 모든 태그에 고정 질문이 있다")
  void everyTagHasFallback() {
    assertThat(RetroQuestionResult.FALLBACK_QUESTIONS).containsOnlyKeys(RetroQuestionResult.TAGS);
  }

  @Test
  @DisplayName("quote에 '실습: ' 같은 칸 이름이 붙어 와도 떼고 출처로 남긴다")
  void stripsFieldLabelFromQuote() {
    Validated v =
        validate(false, List.of(new Question("근거와 숫자", "실습: " + MATERIAL, null, "숫자가 있나요?")));

    assertThat(v.questions().get(0).quote()).isEqualTo(MATERIAL);
  }

  @Test
  @DisplayName("태그가 띄어쓰기·가운뎃점만 다르면 목록의 태그로 맞춘다")
  void normalizesTag() {
    Validated v = validate(false, List.of(new Question("근거와숫자", null, null, "숫자가 있나요?")));

    assertThat(v.questions().get(0).tag()).isEqualTo("근거와 숫자");
  }

  @Test
  @DisplayName("목록에 없는 태그·빈 질문은 그 질문만 버리고, 넘겨주지 않은 첨부 번호는 quote가 실제로 있는 곳(회고 자료)으로 바로잡는다")
  void dropsOnlyBadQuestions() {
    Validated v =
        validate(
            false,
            List.of(
                new Question("엉뚱한 태그", null, null, "버려질 질문"),
                new Question("빠진 칸", null, null, "  "),
                new Question("느낀 점", MATERIAL, 999L, "무엇을 배웠나요?")));

    assertThat(v.questions()).containsExactly(new NewQuestion("느낀 점", MATERIAL, null, "무엇을 배웠나요?"));
  }

  @Test
  @DisplayName("AI가 출처를 비웠어도(회고 자료라고 했어도) quote가 첨부에 있으면 그 첨부를 출처로 저장한다")
  void correctsSourceToAttachment() {
    Validated v =
        validate(false, List.of(new Question("세션과 연결", "지연 로딩과 N+1 문제", null, "세션과 어떻게 이어지나요?")));

    assertThat(v.questions().get(0).quote()).isEqualTo("지연 로딩과 N+1 문제");
    assertThat(v.questions().get(0).sourceAttachmentId()).isEqualTo(502L);
  }

  @Test
  @DisplayName("질문이 4개를 넘으면 앞에서 4개만 쓴다 (기능명세서 R-15 11/11, 전에는 전체 실패)")
  void limitsToFour() {
    List<Question> seven = new ArrayList<>();
    for (int i = 1; i <= 7; i++) {
      seven.add(new Question("빠진 칸", null, null, "질문 " + i));
    }

    assertThat(validate(false, seven).questions()).hasSize(4);
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
