package com.sajarok.retrospective.domain;

import com.sajarok.global.entity.BaseTimeEntity;
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

  public void complete(LocalDateTime now) {
    this.status = RetrospectiveStatus.COMPLETED;
    if (this.completedAt == null) {
      this.completedAt = now;
    }
  }
}
