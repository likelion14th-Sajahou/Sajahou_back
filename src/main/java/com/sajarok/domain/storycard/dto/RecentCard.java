package com.sajarok.domain.storycard.dto;

import com.sajarok.domain.storycard.entity.StoryCard;
import com.sajarok.domain.storycard.entity.StoryCardType;

public record RecentCard(Long cardId, StoryCardType type, Long weekId, String title) {
  public static RecentCard from(StoryCard card) {
    return new RecentCard(card.getId(), card.getType(), card.getWeekId(), card.getTitle());
  }
}
