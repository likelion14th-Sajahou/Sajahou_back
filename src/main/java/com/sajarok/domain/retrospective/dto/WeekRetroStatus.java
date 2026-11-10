package com.sajarok.domain.retrospective.dto;

public record WeekRetroStatus(
    Long weekId,
    RetrospectiveState sessionState,
    RetrospectiveState assignmentState,
    int cardCount) {}
