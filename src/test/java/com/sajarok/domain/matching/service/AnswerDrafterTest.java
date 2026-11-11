package com.sajarok.domain.matching.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.sajarok.domain.matching.ai.AnswerDraftResult;
import com.sajarok.domain.matching.ai.AnswerDraftResult.Piece;
import com.sajarok.domain.matching.ai.MatchingPrompts;
import com.sajarok.domain.matching.ai.MatchingPrompts.PostingText;
import com.sajarok.domain.matching.entity.AnswerDraft;
import com.sajarok.domain.matching.entity.AnswerDraft.Segment;
import com.sajarok.domain.matching.service.CardRecommender.Recommendation;
import com.sajarok.domain.storycard.dto.CardSnapshot;
import com.sajarok.domain.storycard.entity.StoryCardType;
import com.sajarok.global.exception.BusinessException;
import com.sajarok.global.exception.ErrorCode;
import com.sajarok.infra.ai.AiClient;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** 37번 AnswerDrafter 테스트. 실제 OpenAI 대신 가짜 AiClient를 쓴다. */
@ExtendWith(MockitoExtension.class)
class AnswerDrafterTest {

  @Mock private AiClient aiClient;

  @InjectMocks private AnswerDrafter drafter;

  @Test
  @DisplayName("빈칸 초안: 상황·행동·이유·결과 빈칸 4개, '항목 · 채울 내용' 안내, 글자 수 0·빈칸 수 4")
  void blankDraft_hasFourBlanksWithHints() {
    AnswerDraft draft = AnswerDrafter.blankDraft(List.of(), List.of(4L), true);

    assertThat(draft.segments()).extracting(Segment::type).containsOnly("blank");
    assertThat(draft.segments())
        .extracting(Segment::slot)
        .containsExactly("situation", "action", "reason", "result");
    assertThat(draft.segments().get(3).hint()).isEqualTo("결과 · 숫자나 받은 반응을 채워 주세요");
    assertThat(draft.charCount()).isZero();
    assertThat(draft.blankCount()).isEqualTo(4);
    assertThat(draft.missingCompetencyIds()).containsExactly(4L);
    assertThat(draft.noMatchingCard()).isTrue();
  }

  private static final CardSnapshot CARD =
      new CardSnapshot(
          410L,
          StoryCardType.PROJECT,
          null,
          3L,
          "팀 API 연동을 맡아 배포까지",
          null,
          null,
          null,
          null,
          null,
          Set.of(3L));

  private static final Recommendation RECOMMENDATION =
      new Recommendation(List.of(CARD), List.of(), false);

  private AnswerDraft draft() {
    return drafter.draft(
        "협업 경험을 써 주세요",
        700,
        RECOMMENDATION,
        Map.of(410L, CARD),
        new PostingText("카카오", "백엔드", null, "대규모 트래픽 API 개발", null, null));
  }

  @Test
  @DisplayName("AI 초안이 검증을 통과하면 추천 출처(카드 + 지원 공고)와 함께 초안으로 준다")
  void draft_validResponse_keepsSources() {
    when(aiClient.call(
            eq(MatchingPrompts.ANSWER_DRAFT_SYSTEM), anyString(), eq(AnswerDraftResult.class)))
        .thenReturn(
            new AnswerDraftResult(
                List.of(
                    new Piece("text", "팀 API 연동을 맡아 배포까지 했습니다.", 1, null, null),
                    new Piece("blank", null, null, "result", "결과 · 숫자나 받은 반응을 채워 주세요"))));

    AnswerDraft draft = draft();

    assertThat(draft.sources()).isEqualTo(RECOMMENDATION.sources());
    assertThat(draft.segments()).extracting(Segment::type).containsExactly("text", "blank");
    assertThat(draft.noMatchingCard()).isFalse();
  }

  @Test
  @DisplayName("출처 없는 문장(지어낸 경험일 수 있음)이면 AI_FAILED, AiClient의 AI_TIMEOUT은 그대로 올린다")
  void draft_invalidOrTimeout_throws() {
    when(aiClient.call(anyString(), anyString(), eq(AnswerDraftResult.class)))
        .thenReturn(new AnswerDraftResult(List.of(new Piece("text", "출처 없는 문장", null, null, null))))
        .thenThrow(new BusinessException(ErrorCode.AI_TIMEOUT));

    assertThatThrownBy(this::draft).extracting("errorCode").isEqualTo(ErrorCode.AI_FAILED);
    assertThatThrownBy(this::draft).extracting("errorCode").isEqualTo(ErrorCode.AI_TIMEOUT);
  }
}
