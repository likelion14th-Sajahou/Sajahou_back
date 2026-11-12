package com.sajarok.domain.retrospective.entity;

import com.sajarok.domain.retrospective.dto.RetrospectiveDraftRequest;
import com.sajarok.global.entity.BaseTimeEntity;
import com.sajarok.global.exception.BusinessException;
import com.sajarok.global.exception.ErrorCode;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "retrospective")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Retrospective extends BaseTimeEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "user_id", nullable = false)
  private Long userId;

  @Column(nullable = false)
  private Long weekId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private RetrospectiveType type;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private RetrospectiveStatus status;

  @Column(name = "is_rested", nullable = false)
  private boolean rested;

  private Integer currentStep;

  private Boolean hasPractice;

  @Column(columnDefinition = "TEXT")
  private String practiceContent;

  @Enumerated(EnumType.STRING)
  private AssignmentMode assignmentMode;

  @Column(columnDefinition = "MEDIUMTEXT")
  private String assignmentContent;

  @Column(columnDefinition = "TEXT")
  private String codeReview;

  @Column(columnDefinition = "TEXT")
  private String memo;

  @JdbcTypeCode(SqlTypes.CHAR)
  @Column(length = 64)
  private String questionInputHash;

  private LocalDateTime lastSavedAt;

  private LocalDateTime completedAt;

  public static Retrospective start(Long userId, Long weekId, RetrospectiveType type) {
    Retrospective r = new Retrospective();
    r.userId = userId;
    r.weekId = weekId;
    r.type = type;
    r.status = RetrospectiveStatus.IN_PROGRESS;
    r.rested = false;
    r.currentStep = 2;
    return r;
  }

  public static Retrospective startRested(Long userId, Long weekId) {
    Retrospective r = start(userId, weekId, RetrospectiveType.SESSION);
    r.rested = true;
    r.currentStep = null;
    return r;
  }

  public void complete(LocalDateTime now) {
    this.status = RetrospectiveStatus.COMPLETED;
    if (this.completedAt == null) {
      this.completedAt = now;
    }
  }

  /** 15번 휴회 표시·해제(R-06). 세션 회고만, 완료한 회고는 안 된다. 쓰던 내용은 그대로 둔다 */
  public void changeRested(boolean rested) {
    if (type != RetrospectiveType.SESSION || status == RetrospectiveStatus.COMPLETED) {
      throw new BusinessException(ErrorCode.INVALID_STATE);
    }
    this.rested = rested;
    if (!rested && currentStep == null) {
      this.currentStep = 2;
    }
  }

  /** 16번 '처음부터'(R-05). 완료한 회고는 안 된다. 질문·카드 초안·첨부 정리는 서비스가 한다 */
  public void reset() {
    if (status == RetrospectiveStatus.COMPLETED) {
      throw new BusinessException(ErrorCode.INVALID_STATE);
    }
    this.currentStep = 2;
    this.hasPractice = null;
    this.practiceContent = null;
    this.assignmentMode = null;
    this.assignmentContent = null;
    this.codeReview = null;
    this.memo = null;
    this.questionInputHash = null;
    this.lastSavedAt = null;
  }

  /** 14번 임시저장. 요청에 들어 있던 칸만 바꾸고, 저장 시각을 남긴다 */
  public void saveDraft(RetrospectiveDraftRequest draft, LocalDateTime now) {
    if (draft.has("assignmentMode") && this.type != RetrospectiveType.SESSION) {
      throw new BusinessException(ErrorCode.VALIDATION_ERROR, "과제 작성 방식은 세션 회고에서만 골라요.");
    }

    if (draft.has("currentStep") && draft.getCurrentStep() != null) {
      this.currentStep = draft.getCurrentStep();
    }
    if (draft.has("hasPractice")) this.hasPractice = draft.getHasPractice();
    if (draft.has("practiceContent")) this.practiceContent = draft.getPracticeContent();
    if (draft.has("assignmentMode")) this.assignmentMode = draft.getAssignmentMode();
    if (draft.has("assignmentContent")) this.assignmentContent = draft.getAssignmentContent();
    if (draft.has("codeReview")) this.codeReview = draft.getCodeReview();
    if (draft.has("memo")) this.memo = draft.getMemo();
    this.lastSavedAt = now;
  }
}
