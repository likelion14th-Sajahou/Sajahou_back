package com.sajarok.domain.matching.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** cover_letter_question_card의 복합 PK (문항 ID, 카드 ID). */
@Embeddable
@Getter
@EqualsAndHashCode
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class CoverLetterQuestionCardId implements Serializable {

  @Column(name = "cover_letter_question_id", nullable = false)
  private Long coverLetterQuestionId;

  /** 스토리 카드 ID. 카드는 다른 담당자 엔티티라 ID로만 참조한다. */
  @Column(name = "card_id", nullable = false)
  private Long cardId;
}
