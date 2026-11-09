package com.sajarok.domain.matching.entity;

import java.util.List;

/**
 * AI 답 초안. cover_letter_question.answer_draft JSON에 저장한다.
 *
 * <p>charCount·blankCount는 segments로 계산할 수 있어서 저장하지 않는다.
 */
public record AnswerDraft(List<Segment> segments, List<Source> sources) {

  /**
   * 초안 조각 하나.
   *
   * @param type text(문장) / blank(빈칸)
   * @param text 문장 (text일 때)
   * @param source 출처 번호 (text일 때, Source.no)
   * @param slot 빈칸 종류 (blank일 때, 예: result)
   * @param hint 빈칸 안내 문구 (blank일 때)
   */
  public record Segment(String type, String text, Integer source, String slot, String hint) {}

  /**
   * 출처 하나.
   *
   * @param no 출처 번호 (Segment.source가 가리킴)
   * @param kind CARD(스토리 카드) / JOB_POSTING(③ 지원 공고)
   * @param cardId 카드 ID (CARD일 때)
   */
  public record Source(int no, String kind, Long cardId) {}
}
