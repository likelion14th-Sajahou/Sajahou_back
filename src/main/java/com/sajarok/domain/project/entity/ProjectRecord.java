package com.sajarok.domain.project.entity;

import com.sajarok.global.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "project_record")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProjectRecord extends BaseTimeEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "project_id", nullable = false)
  private Project project;

  /** DRAFT = AI와 정리 중(카드 없음), SAVED = 저장됨(카드 있음). */
  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private ProjectRecordStatus status;

  @Column(columnDefinition = "TEXT")
  private String memo;

  @Column(columnDefinition = "TEXT")
  private String did;

  @Column(columnDefinition = "TEXT")
  private String concern;

  @Column(columnDefinition = "TEXT")
  private String decision;

  @Column(columnDefinition = "TEXT")
  private String result;

  @Column(columnDefinition = "TEXT")
  private String nextStep;

  @Column(name = "is_ai_organized", nullable = false)
  private boolean aiOrganized;

  @Builder
  private ProjectRecord(
      Project project,
      ProjectRecordStatus status,
      String memo,
      String did,
      String concern,
      String decision,
      String result,
      String nextStep,
      Boolean aiOrganized) {
    this.project = project;
    this.status = status == null ? ProjectRecordStatus.DRAFT : status;
    this.memo = memo;
    this.did = did;
    this.concern = concern;
    this.decision = decision;
    this.result = result;
    this.nextStep = nextStep;
    this.aiOrganized = aiOrganized != null && aiOrganized;
  }

  /** 메모에 공백 말고 글자가 있는지. */
  public boolean hasMemo() {
    return memo != null && !memo.isBlank();
  }
}
