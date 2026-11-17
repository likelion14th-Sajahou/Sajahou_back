package com.sajarok.domain.retrospective.dto;

import com.sajarok.domain.retrospective.entity.Retrospective;

/** 15번 응답 */
public record RetrospectiveRestResponse(Long id, RetrospectiveState state) {

  /** 시작 전에 휴회했다 해제한 빈 회고는 11번·홈과 같이 NOT_STARTED */
  public static RetrospectiveRestResponse from(Retrospective r) {
    RetrospectiveState state =
        r.isUnsavedBlank() ? RetrospectiveState.NOT_STARTED : RetrospectiveState.of(r);
    return new RetrospectiveRestResponse(r.getId(), state);
  }
}
