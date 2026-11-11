package com.sajarok.domain.retrospective.dto;

import com.sajarok.domain.retrospective.entity.Retrospective;

/** 15번 응답 */
public record RetrospectiveRestResponse(Long id, RetrospectiveState state) {

  public static RetrospectiveRestResponse from(Retrospective r) {
    return new RetrospectiveRestResponse(r.getId(), RetrospectiveState.of(r));
  }
}
