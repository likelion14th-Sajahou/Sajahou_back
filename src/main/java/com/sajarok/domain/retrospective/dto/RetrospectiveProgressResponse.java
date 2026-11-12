package com.sajarok.domain.retrospective.dto;

import com.sajarok.domain.retrospective.entity.Retrospective;
import com.sajarok.global.time.KstTime;
import java.time.OffsetDateTime;

/** 14번 임시저장·16번 처음부터 응답: 지금 어디까지 썼는지 */
public record RetrospectiveProgressResponse(
    Long id, RetrospectiveState state, Integer currentStep, OffsetDateTime lastSavedAt) {

  public static RetrospectiveProgressResponse from(Retrospective r) {
    return new RetrospectiveProgressResponse(
        r.getId(),
        RetrospectiveState.of(r),
        r.getCurrentStep(),
        KstTime.toOffset(r.getLastSavedAt()));
  }
}
