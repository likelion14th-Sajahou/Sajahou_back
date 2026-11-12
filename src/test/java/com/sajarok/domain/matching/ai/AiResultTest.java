package com.sajarok.domain.matching.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sajarok.domain.matching.ai.AnswerDraftResult.Piece;
import com.sajarok.domain.matching.entity.AnswerDraft;
import com.sajarok.domain.matching.entity.AnswerDraft.Source;
import com.sajarok.global.exception.BusinessException;
import com.sajarok.global.exception.ErrorCode;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** AI 응답 확인: 통과하면 저장 모양으로 바뀌고, 어긋나면 AI_FAILED(저장하지 않음). */
class AiResultTest {

  private static final List<Source> SOURCES =
      List.of(new Source(1, "CARD", 410L), new Source(2, "JOB_POSTING", null));

  private static void assertAiFailed(Runnable call) {
    assertThatThrownBy(call::run)
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.AI_FAILED);
  }

  @Test
  @DisplayName("37번: 출처가 맞는 문장과 slot·hint가 있는 빈칸은 초안으로 바뀌고, 없는 역량·맞는 카드 없음도 같이 담긴다")
  void answer_valid_becomesDraft() {
    AnswerDraftResult result =
        new AnswerDraftResult(
            List.of(
                new Piece("text", "API를 맡았습니다.", 1, "무시", null),
                new Piece("blank", "무시", 9, "result", "결과를 채워 주세요")));

    AnswerDraft draft = result.toDraft(SOURCES, List.of(4L), false);

    assertThat(draft.segments().get(0).slot()).isNull();
    assertThat(draft.segments().get(1).source()).isNull();
    assertThat(draft.sources()).isEqualTo(SOURCES);
    assertThat(draft.missingCompetencyIds()).containsExactly(4L);
  }

  @Test
  @DisplayName("37번: 없는 출처 번호·출처 없는 문장·빈 응답·hint 없는 빈칸·모르는 type이면 AI_FAILED")
  void answer_invalid_throwsAiFailed() {
    assertAiFailed(
        () ->
            new AnswerDraftResult(List.of(new Piece("text", "문장", 3, null, null)))
                .toDraft(SOURCES, List.of(), false));
    assertAiFailed(
        () ->
            new AnswerDraftResult(List.of(new Piece("text", "문장", null, null, null)))
                .toDraft(SOURCES, List.of(), false));
    assertAiFailed(() -> new AnswerDraftResult(List.of()).toDraft(SOURCES, List.of(), false));
    assertAiFailed(
        () ->
            new AnswerDraftResult(List.of(new Piece("blank", null, null, "result", " ")))
                .toDraft(SOURCES, List.of(), false));
    assertAiFailed(
        () ->
            new AnswerDraftResult(List.of(new Piece("title", "문장", 1, null, null)))
                .toDraft(SOURCES, List.of(), false));
  }

  @Test
  @DisplayName("36번: 목록 안의 역량이면 통과하고, 하나도 못 찾은 것(빈 목록)도 실패가 아니다")
  void posting_valid_passes() {
    PostingAnalysisResult ok =
        new PostingAnalysisResult(
            List.of(new PostingAnalysisResult.Competency(3L, "협업을 중시")),
            List.of(new PostingAnalysisResult.Question(1, List.of(3L, 4L))));

    assertThat(ok.validate(Set.of(3L, 4L), 1, "협업을 중시").competencies()).hasSize(1);
    assertThat(
            new PostingAnalysisResult(null, null).validate(Set.of(3L), 1, "협업을 중시").competencies())
        .isEmpty();
  }

  @Test
  @DisplayName("36번: evidenceQuote가 공고 원문에 그대로 있으면 두고, 없으면(지어냈거나 고쳐 씀) 역량은 살리고 null로 바꾼다")
  void posting_evidenceNotInOriginal_nullsQuote() {
    PostingAnalysisResult result =
        new PostingAnalysisResult(
                List.of(
                    new PostingAnalysisResult.Competency(3L, "다양한 직군과   협업한 경험"),
                    new PostingAnalysisResult.Competency(4L, "대용량 트래픽 경험 우대")),
                List.of())
            .validate(Set.of(3L, 4L), 1, "카카오\n다양한 직군과 협업한 경험이 있는 분");

    assertThat(result.competencies())
        .extracting(PostingAnalysisResult.Competency::evidenceQuote)
        .containsExactly("다양한 직군과   협업한 경험", null);
  }

  @Test
  @DisplayName("36번: 목록에 없는 역량·공고 역량 6개 이상·문항 역량 4개 이상·없는 문항 번호면 AI_FAILED")
  void posting_invalid_throwsAiFailed() {
    Set<Long> allowed = Set.of(1L, 2L, 3L, 4L, 5L, 6L);
    assertAiFailed(
        () ->
            new PostingAnalysisResult(
                    List.of(new PostingAnalysisResult.Competency(99L, "q")), List.of())
                .validate(allowed, 1, "협업을 중시"));
    assertAiFailed(
        () ->
            new PostingAnalysisResult(
                    List.of(1L, 2L, 3L, 4L, 5L, 6L).stream()
                        .map(id -> new PostingAnalysisResult.Competency(id, "q"))
                        .toList(),
                    List.of())
                .validate(allowed, 1, "협업을 중시"));
    assertAiFailed(
        () ->
            new PostingAnalysisResult(
                    List.of(),
                    List.of(new PostingAnalysisResult.Question(1, List.of(1L, 2L, 3L, 4L))))
                .validate(allowed, 1, "협업을 중시"));
    assertAiFailed(
        () ->
            new PostingAnalysisResult(
                    List.of(), List.of(new PostingAnalysisResult.Question(2, List.of(1L))))
                .validate(allowed, 1, "협업을 중시"));
  }

  @Test
  @DisplayName("프롬프트: 역량 목록·공고·공고 첨부 글자·문항이 user 메시지에 들어가고, 빈 칸은 빠진다")
  void prompts_includeInputs() {
    MatchingPrompts.PostingText posting =
        new MatchingPrompts.PostingText("카카오", null, null, "API 개발", null, null);

    String user =
        MatchingPrompts.postingAnalysisUser(
            posting,
            List.of("우대: 협업 경험"),
            List.of(new MatchingPrompts.CompetencyOption(3L, "협업", "팀과 함께 일하며 결과를 만듦")),
            List.of("협업 경험"));

    assertThat(user)
        .contains("id 3: 협업", "회사: 카카오", "주요 업무·자격 요건: API 개발", "공고 첨부: 우대: 협업 경험", "1. 협업 경험");
    assertThat(user).doesNotContain("부서:", "우대사항:");
    assertThat(MatchingPrompts.POSTING_ANALYSIS_SYSTEM).contains("최대 5개", "최대 3개");
  }
}
