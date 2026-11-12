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

  public static RetrospectiveCompleteResponse of(Retrospective r, Long cardId) {
    boolean assignmentLater =
        r.getType() == RetrospectiveType.SESSION && r.getAssignmentMode() == AssignmentMode.LATER;
    return new RetrospectiveCompleteResponse(
        RetrospectiveState.of(r),
        cardId,
        null, // TODO(주차 담당): weekId → weekNumber
        false, // TODO(주차 담당): completedAt이 파트별 마감보다 늦으면 true
        assignmentLater ? new AssignmentPending(null, null) : null); // TODO(주차 담당): 주차·과제 마감
  }
}
