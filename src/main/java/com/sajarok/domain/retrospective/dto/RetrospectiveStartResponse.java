package com.sajarok.domain.retrospective.dto;

import com.sajarok.domain.retrospective.entity.Retrospective;
import com.sajarok.domain.retrospective.entity.RetrospectiveType;

public record RetrospectiveStartResponse(
    Long id,
    RetrospectiveType type,
    Integer weekNumber,
    RetrospectiveState state,
    Integer currentStep) {

  /**
   * @param weekNumber 회고 주차 번호 (WeekService.findWeekNumbers)
   */
  public static RetrospectiveStartResponse from(Retrospective r, Integer weekNumber) {
    return new RetrospectiveStartResponse(
        r.getId(), r.getType(), weekNumber, RetrospectiveState.of(r), r.getCurrentStep());
  }
}
