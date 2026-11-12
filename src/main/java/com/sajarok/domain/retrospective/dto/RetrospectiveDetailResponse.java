package com.sajarok.domain.retrospective.dto;

import com.sajarok.domain.attachment.dto.AttachmentView;
import com.sajarok.domain.question.dto.QuestionView;
import com.sajarok.domain.retrospective.entity.AssignmentMode;
import com.sajarok.domain.retrospective.entity.Retrospective;
import com.sajarok.domain.retrospective.entity.RetrospectiveType;
import com.sajarok.domain.storycard.dto.StoryCardDetailResponse;
import com.sajarok.global.time.KstTime;
import java.time.OffsetDateTime;
import java.util.List;

/** 13번 회고 상세. 이어 쓰기와 다시 보기에 필요한 것을 한 번에 */
public record RetrospectiveDetailResponse(
    Long id,
    RetrospectiveType type,
    RetrospectiveState state,
    boolean isRested,
    Integer currentStep,
    OffsetDateTime lastSavedAt,
    OffsetDateTime completedAt,
    WeekInfo week,
    Materials materials,
    List<AttachmentView> attachments,
    List<QuestionView> questions,
    StoryCardDetailResponse card) {

  /** 주차 정보. 주차 모듈(BE1)이 생기면 채운다 */
  public record WeekInfo(Integer weekNumber, String sessionTitle, OffsetDateTime sessionDeadline) {}

  /** 2단계 자료 */
  public record Materials(
      Boolean hasPractice,
      String practiceContent,
      AssignmentMode assignmentMode,
      String assignmentContent,
      String codeReview,
      String memo) {}

  /** card는 4단계(19번)를 거치기 전이면 null */
  public static RetrospectiveDetailResponse of(
      Retrospective r,
      List<QuestionView> questions,
      List<AttachmentView> attachments,
      StoryCardDetailResponse card,
      WeekInfo week) {
    return new RetrospectiveDetailResponse(
        r.getId(),
        r.getType(),
        RetrospectiveState.of(r),
        r.isRested(),
        r.getCurrentStep(),
        KstTime.toOffset(r.getLastSavedAt()),
        KstTime.toOffset(r.getCompletedAt()),
        week,
        new Materials(
            r.getHasPractice(),
            r.getPracticeContent(),
            r.getAssignmentMode(),
            r.getAssignmentContent(),
            r.getCodeReview(),
            r.getMemo()),
        attachments,
        questions,
        card);
  }
}
