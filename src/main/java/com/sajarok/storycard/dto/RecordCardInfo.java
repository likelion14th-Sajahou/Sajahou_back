package com.sajarok.storycard.dto;

import java.util.Set;

public record RecordCardInfo(Long recordId, Long cardId, Set<Long> competencyIds) {}
