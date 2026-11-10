package com.sajarok.storycard.dto;

import com.sajarok.storycard.domain.StoryCardType;
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
    Set<Long> competencyIds) {}
