package com.sajarok.domain.retrospective.dto;

import com.sajarok.domain.retrospective.entity.Retrospective;
import com.sajarok.domain.retrospective.entity.RetrospectiveType;

public record RetrospectiveStartResponse(
    Long id,
    RetrospectiveType type,
    Integer weekNumber,
    RetrospectiveState state,
    Integer currentStep) {

  public static RetrospectiveStartResponse from(Retrospective r) {
    return new RetrospectiveStartResponse(
        r.getId(), r.getType(), null, RetrospectiveState.of(r), r.getCurrentStep());
  }
}
