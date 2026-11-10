package com.sajarok.domain.matching.service;

import com.sajarok.domain.matching.entity.AnswerDraft.Source;
import com.sajarok.domain.matching.entity.CoverLetterCardRole;
import com.sajarok.domain.storycard.dto.CardSnapshot;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * 37번 카드 추천. 문항 역량과 카드 태그가 많이 겹치는 카드 먼저 (M-08, H-22).
 *
 * <p>DB·AI를 쓰지 않는 순수 계산이다. 카드는 부르는 쪽이 findPublished로 한 번에 읽어 넘긴다(공개된 회고 카드 + 프로젝트 카드).
 */
@Component
public class CardRecommender {

  /** M-08: ① 추천 카드 1장 + 다음 카드를 ② 보조로 + ③ 지원 공고. */
  private static final int MAX_CARDS = 2;

  /**
   * 1개 이상 겹치는 카드를 점수(겹치는 역량 수) 높은 순으로 고르고, 점수가 같으면 최신 카드 먼저(카드 ID 큰 순) 고른다.
   *
   * <p>1등이 MAIN, 2등이 SUB. 순서가 매번 같아서 테스트할 수 있다.
   */
  public Recommendation recommend(
      Collection<Long> questionCompetencyIds, List<CardSnapshot> cards) {
    Set<Long> wanted =
        questionCompetencyIds == null ? Set.of() : new HashSet<>(questionCompetencyIds);

    List<CardSnapshot> picked =
        cards.stream()
            .filter(card -> overlap(card, wanted) > 0)
            .sorted(
                Comparator.comparingInt((CardSnapshot card) -> overlap(card, wanted))
                    .reversed()
                    .thenComparing(CardSnapshot::cardId, Comparator.reverseOrder()))
            .limit(MAX_CARDS)
            .toList();

    // M-13 "⚑ #역량 경험은 아직 사자록에 없어요": 뽑힌 카드가 아니라 내 공개 카드 "전체"에 그 역량 태그가 없을 때만 없는 역량으로 센다.
    Set<Long> covered = new HashSet<>();
    cards.forEach(card -> covered.addAll(tags(card)));
    List<Long> missing =
        questionCompetencyIds == null
            ? List.of()
            : questionCompetencyIds.stream()
                .distinct()
                .filter(id -> !covered.contains(id))
                .toList();

    return new Recommendation(picked, missing, picked.isEmpty());
  }

  private static int overlap(CardSnapshot card, Set<Long> wanted) {
    return (int) tags(card).stream().filter(wanted::contains).count();
  }

  private static Set<Long> tags(CardSnapshot card) {
    return card.competencyIds() == null ? Set.of() : card.competencyIds();
  }

  /**
   * 추천 결과.
   *
   * @param cards 0~2장. 0번째가 MAIN, 1번째가 SUB
   * @param missingCompetencyIds 내 공개 카드 어디에도 없는 문항 역량 (M-13)
   * @param noMatchingCard 겹치는 카드가 하나도 없음
   */
  public record Recommendation(
      List<CardSnapshot> cards, List<Long> missingCompetencyIds, boolean noMatchingCard) {

    public CoverLetterCardRole roleOf(int index) {
      return index == 0 ? CoverLetterCardRole.MAIN : CoverLetterCardRole.SUB;
    }

    /** AI에게 넘기고 초안에 저장할 출처 목록: 카드 순서대로 1, 2, … 마지막 번호가 ③ 지원 공고. */
    public List<Source> sources() {
      List<Source> sources = new ArrayList<>();
      for (int i = 0; i < cards.size(); i++) {
        sources.add(new Source(i + 1, "CARD", cards.get(i).cardId()));
      }
      sources.add(new Source(cards.size() + 1, "JOB_POSTING", null));
      return sources;
    }
  }
}
