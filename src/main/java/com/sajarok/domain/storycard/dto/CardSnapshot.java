package com.sajarok.domain.storycard.dto;

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
    Set<Long> competencyIds) {}
