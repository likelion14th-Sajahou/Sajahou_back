package com.sajarok.domain.matching.ai;

import com.sajarok.global.exception.BusinessException;
import com.sajarok.global.exception.ErrorCode;
import com.sajarok.infra.ai.EvidenceVerifier;
import java.util.List;
import java.util.Set;

/**
 * 36번 AI 응답: 공고가 원하는 역량과 문항별 역량.
 *
 * <p>엔티티에 바로 넣지 않고 {@link #validate}로 확인한 뒤에 쓴다. AI가 이상한 값을 줘도 DB에 그대로 들어가지 않게 한다.
 */
public record PostingAnalysisResult(List<Competency> competencies, List<Question> questions) {

  public static final int MAX_POSTING_COMPETENCIES = 5;
  public static final int MAX_QUESTION_COMPETENCIES = 3;

  /**
   * @param evidenceQuote 공고 원문에서 그대로 옮긴 근거 문장
   */
  public record Competency(Long competencyId, String evidenceQuote) {}

  /**
   * @param seq 문항 순서 (1부터)
   */
  public record Question(int seq, List<Long> competencyIds) {}

  /**
   * 역량 ID가 넘겨준 목록에 있는지, 개수가 규칙 안인지, 문항 순서가 실제 문항을 가리키는지 확인한다. 아니면 AI_FAILED.
   *
   * <p>역량을 하나도 못 찾은 것(빈 목록)은 실패가 아니다(36번 noCompetencyFound). evidenceQuote가 공고 원문에 그대로 없으면 역량은 살리고
   * evidenceQuote만 null로 바꾼다(30번 quote와 같은 방식).
   *
   * @param postingText 공고 칸과 첨부 글자를 이은 원문
   */
  public PostingAnalysisResult validate(
      Set<Long> allowedCompetencyIds, int questionCount, String postingText) {
    List<Competency> found = competencies == null ? List.of() : competencies;
    List<Question> perQuestion = questions == null ? List.of() : questions;
    if (found.size() > MAX_POSTING_COMPETENCIES
        || found.stream()
            .anyMatch(c -> c == null || !allowedCompetencyIds.contains(c.competencyId()))) {
      throw new BusinessException(ErrorCode.AI_FAILED);
    }
    for (Question q : perQuestion) {
      List<Long> ids = q.competencyIds() == null ? List.of() : q.competencyIds();
      if (q.seq() < 1
          || q.seq() > questionCount
          || ids.size() > MAX_QUESTION_COMPETENCIES
          || !allowedCompetencyIds.containsAll(ids)) {
        throw new BusinessException(ErrorCode.AI_FAILED);
      }
    }
    List<Competency> checked =
        found.stream()
            .map(
                c ->
                    EvidenceVerifier.isQuoted(postingText, c.evidenceQuote())
                        ? new Competency(c.competencyId(), c.evidenceQuote().strip())
                        : new Competency(c.competencyId(), null))
            .toList();
    return new PostingAnalysisResult(checked, perQuestion);
  }
}
