package com.sajarok.domain.matching.entity;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/**
 * AI 답 초안. cover_letter_question.answer_draft JSON에 저장한다.
 *
 * <p>charCount·blankCount는 segments로 계산할 수 있어서 저장하지 않는다. missingCompetencyIds·noMatchingCard는 초안을
 * 만들 때의 값을 그대로 저장한다(새 카드가 생겨도 기존 초안은 그대로, 37번을 다시 부르면 반영).
 *
 * @param missingCompetencyIds 문항 역량 중 맞는 카드가 없던 것 (M-13)
 * @param noMatchingCard 맞는 카드가 하나도 없었는지
 */
public record AnswerDraft(
    List<Segment> segments,
    List<Source> sources,
    List<Long> missingCompetencyIds,
    boolean noMatchingCard) {

  private static final String TEXT = "text";
  private static final String BLANK = "blank";

  /** 빈칸(blank) 조각 수. */
  public int blankCount() {
    if (segments == null) {
      return 0;
    }
    return (int) segments.stream().filter(segment -> BLANK.equals(segment.type())).count();
  }

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
  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record Segment(String type, String text, Integer source, String slot, String hint) {

    /** 출처가 지워진 카드면 글은 남기고 source만 비운다. */
    public Segment withoutSource() {
      return new Segment(type, text, null, slot, hint);
    }
  }

  /**
   * 출처 하나.
   *
   * @param no 출처 번호 (Segment.source가 가리킴)
   * @param kind CARD(스토리 카드) / JOB_POSTING(③ 지원 공고)
   * @param cardId 카드 ID (CARD일 때)
   */
  public record Source(int no, String kind, Long cardId) {}
}
