package com.sajarok.domain.matching.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.groups.Tuple.tuple;

import com.sajarok.domain.matching.ai.AnswerDraftResult.Piece;
import com.sajarok.domain.matching.entity.AnswerDraft;
import com.sajarok.domain.matching.entity.AnswerDraft.Source;
import com.sajarok.global.exception.BusinessException;
import com.sajarok.global.exception.ErrorCode;
import java.util.Arrays;
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

    AnswerDraft draft = result.toDraft(SOURCES, List.of(4L), false, true);

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
                .toDraft(SOURCES, List.of(), false, true));
    assertAiFailed(
        () ->
            new AnswerDraftResult(List.of(new Piece("text", "문장", null, null, null)))
                .toDraft(SOURCES, List.of(), false, true));
    assertAiFailed(() -> new AnswerDraftResult(List.of()).toDraft(SOURCES, List.of(), false, true));
    assertAiFailed(
        () ->
            new AnswerDraftResult(List.of(new Piece("blank", null, null, "result", " ")))
                .toDraft(SOURCES, List.of(), false, true));
    assertAiFailed(
        () ->
            new AnswerDraftResult(List.of(new Piece("title", "문장", 1, null, null)))
                .toDraft(SOURCES, List.of(), false, true));
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
  @DisplayName("36번 QA: AI가 \"우대사항: …\"처럼 칸 이름까지 옮기면 칸 이름을 뗀 문장으로 저장한다 (30·17번 quote와 같게)")
  void posting_labeledEvidence_storesWithoutLabel() {
    PostingAnalysisResult result =
        new PostingAnalysisResult(
                List.of(new PostingAnalysisResult.Competency(3L, "우대사항: 다양한 직군과 협업한 경험")),
                List.of())
            .validate(Set.of(3L), 1, "카카오\n다양한 직군과 협업한 경험");

    assertThat(result.competencies().get(0).evidenceQuote()).isEqualTo("다양한 직군과 협업한 경험");
  }

  @Test
  @DisplayName("37번 QA: 추천 카드에 결과가 없는데 결과 빈칸이 없으면, 출처 (1)이 아닌 첫 문장(회사 연결) 앞에 서버가 빈칸을 넣는다")
  void answer_mainCardWithoutResult_addsResultBlank() {
    AnswerDraft draft =
        new AnswerDraftResult(
                List.of(
                    new Piece("text", "행동 문장", 1, null, null),
                    new Piece("text", "배운 점 문장", 1, null, null),
                    new Piece("text", "회사 연결 문장", 2, null, null)))
            .toDraft(SOURCES, List.of(), false, false);

    assertThat(draft.segments())
        .extracting(AnswerDraft.Segment::type, AnswerDraft.Segment::slot)
        .containsExactly(
            tuple("text", null),
            tuple("text", null),
            tuple("blank", "result"),
            tuple("text", null));
    assertThat(draft.segments().get(2).hint()).isEqualTo("결과 · 숫자나 받은 반응을 채워 주세요");
  }

  @Test
  @DisplayName("37번 QA: 출처 (1) 문장만 있으면 빈칸을 맨 끝에 넣는다")
  void answer_onlyMainSource_addsResultBlankAtEnd() {
    AnswerDraft draft =
        new AnswerDraftResult(List.of(new Piece("text", "행동 문장", 1, null, null)))
            .toDraft(SOURCES, List.of(), false, false);

    assertThat(draft.segments())
        .extracting(AnswerDraft.Segment::slot)
        .containsExactly(null, "result");
  }

  @Test
  @DisplayName("37번 QA: 결과 빈칸이 이미 있거나 추천 카드에 결과가 있으면 빈칸을 더 넣지 않는다")
  void answer_resultBlankPresentOrCardHasResult_unchanged() {
    List<Piece> withBlank =
        List.of(
            new Piece("text", "행동 문장", 1, null, null),
            new Piece("blank", null, null, "result", "결과 · 직접 쓴 안내"));
    List<Piece> noBlank = List.of(new Piece("text", "결과 문장", 1, null, null));

    assertThat(
            new AnswerDraftResult(withBlank).toDraft(SOURCES, List.of(), false, false).blankCount())
        .isEqualTo(1);
    assertThat(new AnswerDraftResult(noBlank).toDraft(SOURCES, List.of(), false, true).blankCount())
        .isZero();
  }

  private static final Set<Long> ALLOWED = Set.of(1L, 2L, 3L, 4L, 5L, 6L);

  private static PostingAnalysisResult.Competency competency(long id) {
    return new PostingAnalysisResult.Competency(id, "협업을 중시");
  }

  @Test
  @DisplayName("36번 너그러운 검증: 공고 역량이 6개면 실패하지 않고 앞에서 5개만 쓴다 (예시 공고 AI_FAILED 원인)")
  void posting_sixCompetencies_keepsFirstFive() {
    PostingAnalysisResult result =
        new PostingAnalysisResult(
                List.of(1L, 2L, 3L, 4L, 5L, 6L).stream().map(AiResultTest::competency).toList(),
                List.of())
            .validate(ALLOWED, 1, "협업을 중시");

    assertThat(result.competencies())
        .extracting(PostingAnalysisResult.Competency::competencyId)
        .containsExactly(1L, 2L, 3L, 4L, 5L);
  }

  @Test
  @DisplayName("36번 너그러운 검증: 문항 역량이 4개면 앞에서 3개만 쓴다")
  void posting_fourQuestionCompetencies_keepsFirstThree() {
    PostingAnalysisResult result =
        new PostingAnalysisResult(
                List.of(), List.of(new PostingAnalysisResult.Question(1, List.of(1L, 2L, 3L, 4L))))
            .validate(ALLOWED, 1, "협업을 중시");

    assertThat(result.questions().get(0).competencyIds()).containsExactly(1L, 2L, 3L);
  }

  @Test
  @DisplayName("36번 너그러운 검증: 목록 밖 id·null 역량은 그것만 버리고(공고·문항 모두), 같은 id는 처음 것만 쓴다")
  void posting_unknownIds_droppedOnly() {
    PostingAnalysisResult result =
        new PostingAnalysisResult(
                Arrays.asList(competency(99L), null, competency(3L), competency(3L)),
                List.of(new PostingAnalysisResult.Question(1, List.of(99L, 4L))))
            .validate(ALLOWED, 1, "협업을 중시");

    assertThat(result.competencies())
        .extracting(PostingAnalysisResult.Competency::competencyId)
        .containsExactly(3L);
    assertThat(result.questions().get(0).competencyIds()).containsExactly(4L);
  }

  @Test
  @DisplayName("36번 너그러운 검증: seq가 범위 밖인 문항은 버리고, 남은 게 없어도 실패가 아니다")
  void posting_seqOutOfRange_dropped() {
    PostingAnalysisResult result =
        new PostingAnalysisResult(
                List.of(competency(99L)),
                List.of(
                    new PostingAnalysisResult.Question(0, List.of(1L)),
                    new PostingAnalysisResult.Question(2, List.of(1L)),
                    new PostingAnalysisResult.Question(1, List.of(2L))))
            .validate(ALLOWED, 1, "협업을 중시");

    assertThat(result.competencies()).isEmpty();
    assertThat(result.questions())
        .extracting(PostingAnalysisResult.Question::seq)
        .containsExactly(1);
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

  @Test
  @DisplayName("36번 프롬프트: 공고 첨부 글자는 하나당 6,000자까지만 넣는다")
  void postingAnalysisUser_cutsLongAttachment() {
    String kept = "가".repeat(MatchingPrompts.MAX_ATTACHMENT_CHARS);

    String user =
        MatchingPrompts.postingAnalysisUser(
            new MatchingPrompts.PostingText("카카오", null, null, null, null, null),
            List.of(kept + "뒷부분"),
            List.of(),
            List.of("협업 경험"));

    assertThat(user).contains("공고 첨부: " + kept + "\n").doesNotContain("뒷부분");
  }
}
