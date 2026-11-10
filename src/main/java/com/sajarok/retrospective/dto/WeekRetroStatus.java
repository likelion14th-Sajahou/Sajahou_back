package com.sajarok.retrospective.dto;

public record WeekRetroStatus(
    Long weekId,
    RetrospectiveState sessionState,
    RetrospectiveState assignmentState,
    int cardCount) {}
