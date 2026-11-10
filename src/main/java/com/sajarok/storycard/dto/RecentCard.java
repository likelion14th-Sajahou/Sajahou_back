package com.sajarok.storycard.dto;

import com.sajarok.storycard.domain.StoryCardType;

public record RecentCard(Long cardId, StoryCardType type, Long weekId, String title) {}
