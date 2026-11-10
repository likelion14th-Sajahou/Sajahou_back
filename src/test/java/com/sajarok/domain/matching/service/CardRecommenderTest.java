package com.sajarok.domain.matching.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.sajarok.domain.matching.entity.AnswerDraft.Source;
import com.sajarok.domain.matching.entity.CoverLetterCardRole;
import com.sajarok.domain.matching.service.CardRecommender.Recommendation;
import com.sajarok.domain.storycard.dto.CardSnapshot;
import com.sajarok.domain.storycard.entity.StoryCardType;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 37번 카드 추천 규칙 (M-08 ① 추천 1장 + ② 보조 1장, M-13 없는 역량). */
class CardRecommenderTest {

  private final CardRecommender recommender = new CardRecommender();

  private static CardSnapshot card(long id, StoryCardType type, Long... tags) {
    return new CardSnapshot(
        id, type, null, null, "카드 " + id, null, null, null, null, null, Set.of(tags));
  }

  @Test
  @DisplayName("겹치는 역량이 많은 카드가 MAIN, 다음이 SUB이고 최대 2장만 고른다 (회고·프로젝트 카드 모두 대상)")
  void recommend_picksByOverlap() {
    Recommendation r =
        recommender.recommend(
            List.of(3L, 4L, 7L),
            List.of(
                card(10, StoryCardType.SESSION, 3L),
                card(11, StoryCardType.PROJECT, 3L, 4L, 7L),
                card(12, StoryCardType.ASSIGNMENT, 3L, 4L),
                card(13, StoryCardType.PROJECT, 9L)));

    assertThat(r.cards()).extracting(CardSnapshot::cardId).containsExactly(11L, 12L);
    assertThat(r.roleOf(0)).isEqualTo(CoverLetterCardRole.MAIN);
    assertThat(r.roleOf(1)).isEqualTo(CoverLetterCardRole.SUB);
    assertThat(r.noMatchingCard()).isFalse();
  }

  @Test
  @DisplayName("점수가 같으면 최신 카드(카드 ID 큰 순) 먼저")
  void recommend_tieBreaksByNewest() {
    Recommendation r =
        recommender.recommend(
            List.of(3L),
            List.of(
                card(10, StoryCardType.SESSION, 3L),
                card(30, StoryCardType.PROJECT, 3L),
                card(20, StoryCardType.PROJECT, 3L)));

    assertThat(r.cards()).extracting(CardSnapshot::cardId).containsExactly(30L, 20L);
  }

  @Test
  @DisplayName("없는 역량은 뽑힌 카드가 아니라 내 공개 카드 전체에 그 태그가 없을 때만 센다 (M-13)")
  void recommend_missingIsAgainstAllCards() {
    Recommendation r =
        recommender.recommend(
            List.of(3L, 4L, 5L, 6L),
            List.of(
                card(21, StoryCardType.PROJECT, 3L, 5L),
                card(22, StoryCardType.PROJECT, 3L, 5L),
                card(13, StoryCardType.SESSION, 4L)));

    // 4번 역량 카드(13)는 점수가 낮아 뽑히지 않았지만 내 카드에 있으므로 "없는 역량"이 아니다. 6번만 어디에도 없다.
    assertThat(r.cards()).extracting(CardSnapshot::cardId).containsExactly(22L, 21L);
    assertThat(r.missingCompetencyIds()).containsExactly(6L);
  }

  @Test
  @DisplayName("겹치는 카드가 없거나 문항 역량이 없으면 noMatchingCard이고, 출처는 ③ 지원 공고 하나뿐이다")
  void recommend_noMatch() {
    Recommendation none =
        recommender.recommend(List.of(4L), List.of(card(11, StoryCardType.PROJECT, 3L)));
    Recommendation noQuestionTags =
        recommender.recommend(null, List.of(card(11, StoryCardType.PROJECT, 3L)));

    assertThat(none.noMatchingCard()).isTrue();
    assertThat(none.missingCompetencyIds()).containsExactly(4L);
    assertThat(noQuestionTags.noMatchingCard()).isTrue();
    assertThat(none.sources()).containsExactly(new Source(1, "JOB_POSTING", null));
  }

  @Test
  @DisplayName("출처 번호는 MAIN=1, SUB=2, 지원 공고=마지막 번호")
  void sources_numbering() {
    Recommendation r =
        recommender.recommend(
            List.of(3L),
            List.of(card(11, StoryCardType.PROJECT, 3L), card(12, StoryCardType.PROJECT, 3L)));

    assertThat(r.sources())
        .containsExactly(
            new Source(1, "CARD", 12L),
            new Source(2, "CARD", 11L),
            new Source(3, "JOB_POSTING", null));
  }
}
