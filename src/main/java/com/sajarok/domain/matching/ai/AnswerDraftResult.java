package com.sajarok.domain.matching.ai;

import com.sajarok.domain.matching.entity.AnswerDraft;
import com.sajarok.domain.matching.entity.AnswerDraft.Segment;
import com.sajarok.domain.matching.entity.AnswerDraft.Source;
import com.sajarok.global.exception.BusinessException;
import com.sajarok.global.exception.ErrorCode;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 37번 AI 응답: 답 초안 조각들.
 *
 * <p>엔티티(AnswerDraft)와 따로 둔다. {@link #toDraft}에서 확인을 통과해야만 초안으로 바뀐다.
 */
public record AnswerDraftResult(List<Piece> segments) {

  private static final String TEXT = "text";
  private static final String BLANK = "blank";

  /**
   * 조각 하나. text면 text·source가, blank면 slot·hint가 있어야 한다.
   *
   * @param source 출처 번호 (프롬프트로 넘긴 출처의 no)
   */
  public record Piece(String type, String text, Integer source, String slot, String hint) {}

  /**
   * 확인한 뒤 저장할 초안으로 바꾼다. 하나라도 어긋나면 AI_FAILED(저장하지 않음).
   *
   * <p>출처 없는 문장은 지어낸 경험일 수 있어서, 고쳐서 저장하지 않고 다시 만들게 한다.
   */
  public AnswerDraft toDraft(
      List<Source> sources, List<Long> missingCompetencyIds, boolean noMatchingCard) {
    if (segments == null || segments.isEmpty()) {
      throw new BusinessException(ErrorCode.AI_FAILED);
    }
    Set<Integer> sourceNos = sources.stream().map(Source::no).collect(Collectors.toSet());
    List<Segment> checked = segments.stream().map(p -> check(p, sourceNos)).toList();
    return new AnswerDraft(checked, sources, missingCompetencyIds, noMatchingCard);
  }

  private static Segment check(Piece piece, Set<Integer> sourceNos) {
    if (piece == null) {
      throw new BusinessException(ErrorCode.AI_FAILED);
    }
    if (TEXT.equals(piece.type())
        && hasText(piece.text())
        && piece.source() != null
        && sourceNos.contains(piece.source())) {
      return new Segment(TEXT, piece.text(), piece.source(), null, null);
    }
    if (BLANK.equals(piece.type()) && hasText(piece.slot()) && hasText(piece.hint())) {
      return new Segment(BLANK, null, null, piece.slot(), piece.hint());
    }
    throw new BusinessException(ErrorCode.AI_FAILED);
  }

  private static boolean hasText(String value) {
    return value != null && !value.isBlank();
  }
}
