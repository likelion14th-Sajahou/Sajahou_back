package com.sajarok.domain.retrospective.dto;

import com.sajarok.domain.retrospective.entity.AssignmentMode;
import com.sajarok.domain.retrospective.entity.Retrospective;
import com.sajarok.domain.retrospective.entity.RetrospectiveType;
import com.sajarok.global.time.KstTime;
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
   * @param deadline 이 회고의 마감(세션 회고면 세션 마감, 과제 회고면 과제 마감). 모르면 null이고 지각이 아니다
   * @param assignmentDeadline 그 주 과제 회고 마감. assignmentPending에 쓴다
   */
  public static RetrospectiveCompleteResponse of(
      Retrospective r,
      Long cardId,
      Integer weekNumber,
      OffsetDateTime deadline,
      OffsetDateTime assignmentDeadline) {
    boolean assignmentLater =
        r.getType() == RetrospectiveType.SESSION && r.getAssignmentMode() == AssignmentMode.LATER;
    // 처음 완료한 시각(completedAt은 다시 저장해도 안 바뀜)이 마감보다 늦으면 지각
    OffsetDateTime completedAt = KstTime.toOffset(r.getCompletedAt());
    boolean isLate = deadline != null && completedAt != null && completedAt.isAfter(deadline);
    return new RetrospectiveCompleteResponse(
        RetrospectiveState.of(r),
        cardId,
        weekNumber,
        isLate,
        assignmentLater ? new AssignmentPending(weekNumber, assignmentDeadline) : null);
  }
}
