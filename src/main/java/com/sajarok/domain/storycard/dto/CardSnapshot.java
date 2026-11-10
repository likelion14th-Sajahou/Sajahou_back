package com.sajarok.domain.storycard.dto;

import com.sajarok.domain.storycard.entity.StoryCard;
import com.sajarok.domain.storycard.entity.StoryCardType;
import java.util.Set;

public record CardSnapshot(
    Long cardId,
    StoryCardType type,
    Long weekId,
    Long projectId,
    String title,
    String situation,
    String action,
    String reason,
    String result,
    String learned,
    Set<Long> competencyIds) {

  public static CardSnapshot from(StoryCard card) {
    return new CardSnapshot(
        card.getId(),
        card.getType(),
        card.getWeekId(),
        card.getProjectId(),
        card.getTitle(),
        card.getSituation(),
        card.getAction(),
        card.getReason(),
        card.getResult(),
        card.getLearned(),
        Set.copyOf(card.getCompetencyIds()));
  }
}
