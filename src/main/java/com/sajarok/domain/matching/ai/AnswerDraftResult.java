package com.sajarok.domain.matching.ai;

import com.sajarok.domain.matching.entity.AnswerDraft;
import com.sajarok.domain.matching.entity.AnswerDraft.Segment;
import com.sajarok.domain.matching.entity.AnswerDraft.Source;
import com.sajarok.global.exception.BusinessException;
import com.sajarok.global.exception.ErrorCode;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 37번 AI 응답: 답 초안 조각들.
 *
 * <p>엔티티(AnswerDraft)와 따로 둔다. {@link #toDraft}에서 확인을 통과해야만 초안으로 바뀐다.
 */
public record AnswerDraftResult(List<Piece> segments) {

  private static final Logger log = LoggerFactory.getLogger(AnswerDraftResult.class);

  private static final String TEXT = "text";
  private static final String BLANK = "blank";
  private static final int MAIN_SOURCE = 1;
  private static final Segment RESULT_BLANK =
      new Segment(BLANK, null, null, "result", "결과 · 숫자나 받은 반응을 채워 주세요");

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
   *
   * <p>추천 카드에 결과가 없는데 AI가 결과 빈칸을 빠뜨리면(M-10) 서버가 빈칸을 넣는다. 시연 중 실패보다 낫다고 보고 AI_FAILED로 막지 않는다.
   *
   * @param mainCardHasResult 출처 (1) 추천 카드에 결과가 있는지
   */
  public AnswerDraft toDraft(
      List<Source> sources,
      List<Long> missingCompetencyIds,
      boolean noMatchingCard,
      boolean mainCardHasResult) {
    if (segments == null || segments.isEmpty()) {
      throw new BusinessException(ErrorCode.AI_FAILED);
    }
    Set<Integer> sourceNos = sources.stream().map(Source::no).collect(Collectors.toSet());
    List<Segment> checked = segments.stream().map(p -> check(p, sourceNos)).toList();
    if (!mainCardHasResult && checked.stream().noneMatch(AnswerDraftResult::isResultBlank)) {
      log.info("37번 추천 카드에 결과가 없는데 결과 빈칸이 없어 서버가 넣음");
      checked = withResultBlank(checked);
    }
    return new AnswerDraft(checked, sources, missingCompetencyIds, noMatchingCard);
  }

  /** 출처 (1)이 아닌 첫 문장(다른 경험 연결·회사 연결) 앞에 넣는다. 서버는 출처 (1) 문장 중 어느 것이 결과 자리인지 알 수 없어서다. */
  private static List<Segment> withResultBlank(List<Segment> checked) {
    List<Segment> result = new ArrayList<>(checked);
    int at = result.size();
    for (int i = 0; i < result.size(); i++) {
      Segment s = result.get(i);
      if (TEXT.equals(s.type()) && !Integer.valueOf(MAIN_SOURCE).equals(s.source())) {
        at = i;
        break;
      }
    }
    result.add(at, RESULT_BLANK);
    return result;
  }

  private static boolean isResultBlank(Segment s) {
    return BLANK.equals(s.type()) && "result".equals(s.slot());
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
