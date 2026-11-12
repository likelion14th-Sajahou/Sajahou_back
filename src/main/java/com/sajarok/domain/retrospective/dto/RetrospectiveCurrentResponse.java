package com.sajarok.domain.retrospective.dto;

import com.sajarok.domain.retrospective.entity.AssignmentMode;
import com.sajarok.domain.retrospective.entity.Retrospective;
import com.sajarok.domain.week.service.WeekService.CurrentWeek;
import com.sajarok.domain.week.service.WeekService.WeekSchedule;
import com.sajarok.global.time.KstTime;
import java.time.OffsetDateTime;
import java.util.List;

/** 11번 이번 주 회고 조회 응답 */
public record RetrospectiveCurrentResponse(
    WeekView week, SessionView session, List<OpenAssignment> openAssignments) {

  /** 이번 주 정보. 파트 일정이 없으면 제목·마감이 null */
  public record WeekView(
      Long weekId,
      int weekNumber,
      String sessionTitle,
      OffsetDateTime sessionDeadline,
      OffsetDateTime assignmentDeadline) {

    public static WeekView from(CurrentWeek w) {
      return new WeekView(
          w.weekId(),
          w.weekNumber(),
          w.sessionTitle(),
          w.sessionDeadline(),
          w.assignmentDeadline());
    }
  }

  /** 이번 주 세션 회고. 아직 없으면 id·currentStep·assignmentMode·lastSavedAt이 null이고 NOT_STARTED */
  public record SessionView(
      Long id,
      RetrospectiveState state,
      Integer currentStep,
      AssignmentMode assignmentMode,
      OffsetDateTime lastSavedAt) {

    public static SessionView of(Retrospective r) {
      if (r == null) {
        return new SessionView(null, RetrospectiveState.NOT_STARTED, null, null, null);
      }
      return new SessionView(
          r.getId(),
          RetrospectiveState.of(r),
          r.getCurrentStep(),
          r.getAssignmentMode(),
          KstTime.toOffset(r.getLastSavedAt()));
    }
  }

  /**
   * 과제를 '나중에 쓸게요(LATER)'로 고른 주의 과제 회고 하나. 시작 전이면 id·currentStep·lastSavedAt이 null이고 NOT_STARTED.
   *
   * @param deadline 그 주 과제 회고 마감. 파트 일정이 없으면 null
   */
  public record OpenAssignment(
      Long id,
      Integer weekNumber,
      RetrospectiveState state,
      Integer currentStep,
      OffsetDateTime lastSavedAt,
      OffsetDateTime deadline) {

    /**
     * @param assignment 그 주 과제 회고. 아직 시작 안 했으면 null
     */
    public static OpenAssignment of(Retrospective assignment, WeekSchedule schedule) {
      if (assignment == null) {
        return new OpenAssignment(
            null,
            schedule.weekNumber(),
            RetrospectiveState.NOT_STARTED,
            null,
            null,
            schedule.assignmentDeadline());
      }
      return new OpenAssignment(
          assignment.getId(),
          schedule.weekNumber(),
          RetrospectiveState.of(assignment),
          assignment.getCurrentStep(),
          KstTime.toOffset(assignment.getLastSavedAt()),
          schedule.assignmentDeadline());
    }
  }
}
