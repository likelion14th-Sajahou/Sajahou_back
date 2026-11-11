package com.sajarok.domain.retrospective.dto;

import com.sajarok.domain.retrospective.entity.Retrospective;
import com.sajarok.domain.retrospective.entity.RetrospectiveStatus;

public enum RetrospectiveState {
  NOT_STARTED,
  IN_PROGRESS,
  COMPLETED,
  RESTED;

  public static RetrospectiveState of(Retrospective r) {
    if (r.isRested()) {
      return RESTED;
    }
    return r.getStatus() == RetrospectiveStatus.COMPLETED ? COMPLETED : IN_PROGRESS;
  }
}
