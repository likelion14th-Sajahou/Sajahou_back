package com.sajarok.domain.matching.entity;

import java.util.List;

/**
 * AI 답 초안. cover_letter_question.answer_draft JSON에 저장한다.
 *
 * <p>charCount·blankCount는 segments로 계산할 수 있어서 저장하지 않는다.
 */
public record AnswerDraft(List<Segment> segments, List<Source> sources) {

  private static final String TEXT = "text";

  /** 초안 글자 수. 문장(text) 조각만 세고 빈칸(blank)은 세지 않는다. */
  public int charCount() {
    if (segments == null) {
      return 0;
    }
    return segments.stream()
        .filter(segment -> TEXT.equals(segment.type()))
        .mapToInt(segment -> countChars(segment.text()))
        .sum();
  }

  /**
   * 자소서 글자 수 기준. 공백·줄바꿈을 포함하고, 이모지 등도 눈에 보이는 1글자로 센다 (FE도 같은 기준).
   *
   * <p>String.length()는 이모지를 2로 세므로 코드 포인트 수를 쓴다.
   */
  public static int countChars(String text) {
    return text == null ? 0 : text.codePointCount(0, text.length());
  }

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
