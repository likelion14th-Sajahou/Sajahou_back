package com.sajarok.domain.retrospective.dto;

import java.util.List;

public record HomeRetroSummary(
    Long sessionRetrospectiveId,
    RetrospectiveState state,
    List<Long> stickerWeekIds,
    boolean showThisWeekSlot) {}
