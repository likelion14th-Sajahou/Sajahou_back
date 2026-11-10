package com.sajarok.domain.matching.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;

import com.sajarok.domain.matching.dto.AnswerResponse;
import com.sajarok.domain.matching.entity.AnswerDraft;
import com.sajarok.domain.matching.entity.AnswerDraft.Segment;
import com.sajarok.domain.matching.entity.AnswerDraft.Source;
import com.sajarok.domain.matching.entity.CoverLetterCardRole;
import com.sajarok.domain.matching.entity.CoverLetterQuestion;
import com.sajarok.domain.storycard.dto.CardSnapshot;
import com.sajarok.domain.storycard.entity.StoryCardType;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/** 저장된 초안 → 37·39번 answer 모양 변환 규칙. */
class AnswerAssemblerTest {

  private final AnswerAssembler assembler = new AnswerAssembler();

  private static CardSnapshot card(Long id, Long projectId) {
    return new CardSnapshot(
        id,
        StoryCardType.PROJECT,
        null,
        projectId,
        "카드 " + id,
        null,
        null,
        null,
        null,
        null,
        Set.of());
  }

  /** 출처 1 = 카드 410, 출처 2 = 카드 411, 출처 3 = 지원 공고. 빈칸 1개, 없는 역량 [4]. */
  private static CoverLetterQuestion questionWithDraft() {
    CoverLetterQuestion question = CoverLetterQuestion.builder().seq(1).content("협업 경험").build();
    ReflectionTestUtils.setField(question, "id", 55L);
    ReflectionTestUtils.setField(
        question,
        "answerDraft",
        new AnswerDraft(
            List.of(
                new Segment("text", "API를 맡았다.", 1, null, null),
                new Segment("text", "배포했다.", 2, null, null),
                new Segment("blank", null, null, "result", "결과를 채워 주세요"),
                new Segment("text", "귀사에서도.", 3, null, null)),
            List.of(
                new Source(1, "CARD", 410L),
                new Source(2, "CARD", 411L),
                new Source(3, "JOB_POSTING", null)),
            List.of(4L),
            false));
    return question;
  }

  @Test
  @DisplayName("초안이 없으면 null")
  void assemble_noDraft_returnsNull() {
    CoverLetterQuestion question = CoverLetterQuestion.builder().seq(1).content("협업 경험").build();

    assertThat(assembler.assemble(question, Map.of(), Map.of(), Map.of())).isNull();
  }

  @Test
  @DisplayName("카드 출처에 역할·카드 정보·프로젝트 이름을 붙이고, 글자 수·빈칸 수·공고 사용·저장된 없는 역량을 그대로 준다")
  void assemble_buildsCardsAndCounts() {
    AnswerResponse answer =
        assembler.assemble(
            questionWithDraft(),
            Map.of(410L, CoverLetterCardRole.MAIN, 411L, CoverLetterCardRole.SUB),
            Map.of(410L, card(410L, 3L), 411L, card(411L, null)),
            Map.of(3L, "사자록"));

    assertThat(answer.questionId()).isEqualTo(55L);
    assertThat(answer.cards())
        .extracting(
            AnswerResponse.CardRef::no,
            AnswerResponse.CardRef::role,
            AnswerResponse.CardRef::projectName)
        .containsExactly(
            tuple(1, CoverLetterCardRole.MAIN, "사자록"), tuple(2, CoverLetterCardRole.SUB, null));
    assertThat(answer.charCount()).isEqualTo(9 + 5 + 6);
    assertThat(answer.blankCount()).isEqualTo(1);
    assertThat(answer.usesJobPosting()).isTrue();
    assertThat(answer.missingCompetencies())
        .extracting(AnswerResponse.MissingCompetency::competencyId)
        .containsExactly(4L);
    assertThat(answer.noMatchingCard()).isFalse();
  }

  @Test
  @DisplayName("지워졌거나 비공개가 된 카드는 cards에서 빼고, 그 문장은 글을 남긴 채 source만 null로 바꾼다")
  void assemble_deadCard_dropsCardAndClearsSource() {
    AnswerResponse answer =
        assembler.assemble(
            questionWithDraft(),
            Map.of(410L, CoverLetterCardRole.MAIN, 411L, CoverLetterCardRole.SUB),
            Map.of(410L, card(410L, null)),
            Map.of());

    assertThat(answer.cards()).extracting(AnswerResponse.CardRef::cardId).containsExactly(410L);
    assertThat(answer.segments().get(1).text()).isEqualTo("배포했다.");
    assertThat(answer.segments().get(1).source()).isNull();
    assertThat(answer.segments().get(0).source()).isEqualTo(1);
    assertThat(answer.segments().get(3).source()).isEqualTo(3);
  }
}
