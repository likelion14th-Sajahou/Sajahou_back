package com.sajarok.domain.matching.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 답에 쓴 카드 (문항-카드 연결).
 *
 * <p>테이블에 created_at·updated_at이 없어서 BaseTimeEntity를 상속하지 않는다. 문항이나 카드를 지우면 DB의 ON DELETE CASCADE로
 * 함께 지워진다.
 */
@Entity
@Table(name = "cover_letter_question_card")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CoverLetterQuestionCard {

  @EmbeddedId private CoverLetterQuestionCardId id;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private CoverLetterCardRole role;

  public CoverLetterQuestionCard(
      Long coverLetterQuestionId, Long cardId, CoverLetterCardRole role) {
    this.id = new CoverLetterQuestionCardId(coverLetterQuestionId, cardId);
    this.role = role;
  }
}
