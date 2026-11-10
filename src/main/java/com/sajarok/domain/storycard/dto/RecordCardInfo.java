package com.sajarok.domain.storycard.dto;

import java.util.Set;

public record RecordCardInfo(Long recordId, Long cardId, Set<Long> competencyIds) {}
