package com.sajarok.domain.storycard.dto;

import com.sajarok.domain.storycard.entity.StoryCard;
import com.sajarok.domain.storycard.entity.StoryCardType;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public record StoryCardListResponse(int totalCount, List<CardItem> cards) {

  /** 목록의 카드 한 장 */
  public record CardItem(
      Long cardId,
      StoryCardType type,
      Integer weekNumber,
      Long projectId,
      String projectName,
      String title,
      List<StoryCardDetailResponse.CompetencyView> competencies,
      List<String> evidenceSummary) {

    /**
     * projectName·recordDate는 회고 카드면 null.
     *
     * @param competencyNames 목록 전체 카드의 역량 ID → 이름 (한 번에 가져온 것)
     * @param weekNumber 회고 카드의 주차 번호. 프로젝트 카드는 null
     */
    public static CardItem of(
        StoryCard card,
        String projectName,
        LocalDate recordDate,
        Map<Long, String> competencyNames,
        Integer weekNumber) {
      List<StoryCardDetailResponse.CompetencyView> competencies =
          card.getCompetencyIds().stream()
              .sorted()
              .map(id -> new StoryCardDetailResponse.CompetencyView(id, competencyNames.get(id)))
              .toList();

      List<String> evidenceSummary = recordDate == null ? List.of() : List.of(recordDate + " 기록");

      return new CardItem(
          card.getId(),
          card.getType(),
          weekNumber,
          card.getProjectId(),
          projectName,
          card.getTitle(),
          competencies,
          evidenceSummary);
    }
  }
}
