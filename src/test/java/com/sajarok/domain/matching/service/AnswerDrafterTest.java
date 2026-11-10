package com.sajarok.domain.matching.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.sajarok.domain.matching.ai.MatchingPrompts.PostingText;
import com.sajarok.domain.matching.entity.AnswerDraft;
import com.sajarok.domain.matching.entity.AnswerDraft.Segment;
import com.sajarok.domain.matching.service.CardRecommender.Recommendation;
import com.sajarok.domain.storycard.dto.CardSnapshot;
import com.sajarok.domain.storycard.entity.StoryCardType;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 37번 AnswerDrafter 테스트 (AiClient 연결 전): 빈칸 초안 모양. */
class AnswerDrafterTest {

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

  @Test
  @DisplayName("AiClient 연결 전 draft: 추천 출처(카드 + 지원 공고)를 그대로 담은 빈칸 초안을 준다 (카드 목록이 응답에 보이게)")
  void draft_beforeAiClient_keepsRecommendationSources() {
    CardSnapshot card =
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
    Recommendation recommendation = new Recommendation(List.of(card), List.of(), false);

    AnswerDraft draft =
        new AnswerDrafter()
            .draft(
                "협업 경험을 써 주세요",
                700,
                recommendation,
                Map.of(410L, card),
                new PostingText("카카오", "백엔드", null, "대규모 트래픽 API 개발", null, null));

    assertThat(draft.sources()).isEqualTo(recommendation.sources());
    assertThat(draft.blankCount()).isEqualTo(4);
    assertThat(draft.noMatchingCard()).isFalse();
  }
}
