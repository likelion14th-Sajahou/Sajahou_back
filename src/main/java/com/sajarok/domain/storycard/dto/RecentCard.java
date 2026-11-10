package com.sajarok.domain.storycard.dto;

import com.sajarok.domain.storycard.entity.StoryCardType;

public record RecentCard(Long cardId, StoryCardType type, Long weekId, String title) {}
