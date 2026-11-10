package com.sajarok.question.domain;

import com.sajarok.global.entity.BaseTimeEntity;
import com.sajarok.global.exception.BusinessException;
import com.sajarok.global.exception.ErrorCode;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "ai_question")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AiQuestion extends BaseTimeEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "retrospective_id")
  private Long retrospectiveId;

  @Column(name = "project_record_id")
  private Long projectRecordId;

  @Column(name = "project_id")
  private Long projectId;

  @Column(nullable = false)
  private int seq;

  @Column(length = 50, nullable = false)
  private String tag;

  @Column(columnDefinition = "TEXT")
  private String quote;

  @Column(name = "source_attachment_id")
  private Long sourceAttachmentId;

  @Column(columnDefinition = "TEXT", nullable = false)
  private String content;

  @Column(columnDefinition = "TEXT")
  private String answer;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private QuestionStatus status;

  public static AiQuestion create(
      QuestionOwner owner,
      int seq,
      String tag,
      String quote,
      Long sourceAttachmentId,
      String content) {
    AiQuestion q = new AiQuestion();

    switch (owner.kind()) {
      case RETROSPECTIVE -> q.retrospectiveId = owner.id();
      case PROJECT_RECORD -> q.projectRecordId = owner.id();
      case PROJECT -> q.projectId = owner.id();
    }

    q.seq = seq;
    q.tag = tag;
    q.quote = quote;
    q.sourceAttachmentId = sourceAttachmentId;
    q.content = content;
    q.status = QuestionStatus.PENDING;

    return q;
  }

  public void answer(String text) {
    if (text == null || text.isBlank()) {
      throw new BusinessException(ErrorCode.VALIDATION_ERROR, "답을 적거나 건너뛰기를 눌러 주세요.");
    }
    this.answer = text;
    this.status = QuestionStatus.ANSWERED;
  }

  public void skip() {
    this.answer = null;
    this.status = QuestionStatus.SKIPPED;
  }
}
