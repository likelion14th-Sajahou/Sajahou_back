package com.sajarok.domain.retrospective.entity;

import com.sajarok.domain.retrospective.dto.RetrospectiveDraftRequest;
import com.sajarok.global.entity.BaseTimeEntity;
import com.sajarok.global.exception.BusinessException;
import com.sajarok.global.exception.ErrorCode;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.stream.Stream;
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
    clearToStep2();
  }

  /**
   * 24번 회고 카드 삭제: 처음 시작한 것처럼 빈 2단계로 되돌린다. 완료한 회고도 작성 중으로 돌아간다. 질문·카드·첨부 정리는 서비스가 한다.
   *
   * <p>completedAt(처음 완료한 시각)은 지우지 않는다. '한 번 완료한 회고'라는 표시로 남겨, 마감 + 7일이 지났어도 다시 쓰고 완료할 수 있게 하고(지운
   * 순간 다시 못 쓰게 되는 것을 막음), 지각 여부도 처음 완료 기준을 그대로 쓴다.
   */
  public void restart() {
    this.status = RetrospectiveStatus.IN_PROGRESS;
    clearToStep2();
  }

  /** 한 번이라도 완료한 회고면 true (카드를 지워 다시 쓰는 중이어도) */
  public boolean wasCompleted() {
    return status == RetrospectiveStatus.COMPLETED || completedAt != null;
  }

  /**
   * 카드를 지워 처음으로 돌아간 뒤({@link #restart}) 아직 아무것도 저장하지 않은 회고. 이번 주 화면(11번)·홈에서는 처음 쓰는 것처럼 '시작 전'으로 보여
   * 준다. 한 번 저장(14번)하면 다시 '작성 중'으로 보인다.
   */
  public boolean isRestartedBlank() {
    return status == RetrospectiveStatus.IN_PROGRESS
        && !rested
        && completedAt != null
        && lastSavedAt == null;
  }

  private void clearToStep2() {
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

  /** 17번: 질문을 만들 때의 자료 해시를 남긴다 */
  public void recordQuestionInput(String hash) {
    this.questionInputHash = hash;
  }

  /** 17번: 실습·과제·코드 리뷰·메모 중 하나라도 글이 있으면 true */
  public boolean hasMaterial() {
    return Stream.of(practiceContent, assignmentContent, codeReview, memo)
        .anyMatch(v -> v != null && !v.isBlank());
  }
}
