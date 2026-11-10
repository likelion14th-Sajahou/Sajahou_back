package com.sajarok.domain.matching.entity;

import com.sajarok.global.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.List;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** 자소서 문항과 답. */
@Entity
@Table(name = "cover_letter_question")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CoverLetterQuestion extends BaseTimeEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "job_posting_id", nullable = false)
  private JobPosting jobPosting;

  @Column(nullable = false)
  private int seq;

  @Column(nullable = false, columnDefinition = "TEXT")
  private String content;

  /** 글자 수 제한. 없으면 NULL. */
  private Integer charLimit;

  /** 문항이 묻는 역량 ID 배열. AI로 찾기 전에는 NULL. */
  @JdbcTypeCode(SqlTypes.JSON)
  private List<Long> competencyIds;

  /** AI 답 초안. 만들기 전에는 NULL. */
  @JdbcTypeCode(SqlTypes.JSON)
  private AnswerDraft answerDraft;

  /** 직접 고친 답. NULL이면 초안 그대로. */
  @Column(columnDefinition = "TEXT")
  private String editedAnswer;

  @Builder
  private CoverLetterQuestion(JobPosting jobPosting, int seq, String content, Integer charLimit) {
    this.jobPosting = jobPosting;
    this.seq = seq;
    this.content = content;
    this.charLimit = charLimit;
  }

  /** 36번: AI가 고른(검증한) 문항별 역량 ID를 저장한다. 없으면 빈 배열(분석 전 NULL과 구분). */
  public void applyCompetencyIds(List<Long> competencyIds) {
    this.competencyIds = List.copyOf(competencyIds);
  }

  /** 직접 고친 답을 저장한다. null이면 AI 초안으로 되돌린다. charLimit을 넘어도 막지 않는다. */
  public void editAnswer(String editedAnswer) {
    this.editedAnswer = editedAnswer;
  }

  /** 지금 보이는 답의 글자 수. 고친 답이 있으면 그 글자 수, 없으면 초안 글자 수. */
  public int charCount() {
    if (editedAnswer != null) {
      return AnswerDraft.countChars(editedAnswer);
    }
    return answerDraft == null ? 0 : answerDraft.charCount();
  }
}
