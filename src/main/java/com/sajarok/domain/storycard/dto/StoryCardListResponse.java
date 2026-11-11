package com.sajarok.domain.storycard.dto;

import com.sajarok.domain.storycard.entity.StoryCard;
import com.sajarok.domain.storycard.entity.StoryCardType;
import java.time.LocalDate;
import java.util.List;

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

    /** projectName·recordDate는 회고 카드면 null */
    public static CardItem of(StoryCard card, String projectName, LocalDate recordDate) {
      List<StoryCardDetailResponse.CompetencyView> competencies =
          card.getCompetencyIds().stream()
              .sorted()
              .map(id -> new StoryCardDetailResponse.CompetencyView(id, null))
              .toList();

      List<String> evidenceSummary = recordDate == null ? List.of() : List.of(recordDate + " 기록");

      return new CardItem(
          card.getId(),
          card.getType(),
          null, // weekNumber는 null (TODO(주차 담당))
          card.getProjectId(),
          projectName,
          card.getTitle(),
          competencies,
          evidenceSummary);
    }
  }
}
