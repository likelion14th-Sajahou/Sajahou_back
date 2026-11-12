package com.sajarok.domain.retrospective.dto;

import com.sajarok.domain.retrospective.entity.AssignmentMode;
import com.sajarok.domain.retrospective.entity.Retrospective;
import com.sajarok.domain.retrospective.entity.RetrospectiveType;
import java.time.OffsetDateTime;

/** 20번 회고 저장 응답 */
public record RetrospectiveCompleteResponse(
    RetrospectiveState state,
    Long cardId,
    Integer weekNumber,
    boolean isLate,
    AssignmentPending assignmentPending) {

  /** 과제를 '나중에 쓸게요(LATER)'로 고른 세션 회고일 때만. 완료 화면의 '과제 회고가 남았어요'(R-22) */
  public record AssignmentPending(Integer weekNumber, OffsetDateTime deadline) {}

  /**
   * @param weekNumber 회고 주차 번호. 과제 회고도 같은 주차라 assignmentPending에도 쓴다
   */
  public static RetrospectiveCompleteResponse of(Retrospective r, Long cardId, Integer weekNumber) {
    boolean assignmentLater =
        r.getType() == RetrospectiveType.SESSION && r.getAssignmentMode() == AssignmentMode.LATER;
    return new RetrospectiveCompleteResponse(
        RetrospectiveState.of(r),
        cardId,
        weekNumber,
        false, // TODO(주차 담당): completedAt이 파트별 마감보다 늦으면 true (주차+파트 마감 조회 필요)
        assignmentLater
            ? new AssignmentPending(weekNumber, null) // TODO(주차 담당): 과제 마감
            : null);
  }
}
