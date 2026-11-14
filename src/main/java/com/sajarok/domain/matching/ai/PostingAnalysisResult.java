package com.sajarok.domain.matching.ai;

import com.sajarok.infra.ai.EvidenceVerifier;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 36번 AI 응답: 공고가 원하는 역량과 문항별 역량.
 *
 * <p>엔티티에 바로 넣지 않고 {@link #validate}로 확인한 뒤에 쓴다. AI가 이상한 값을 줘도 DB에 그대로 들어가지 않게 한다.
 */
public record PostingAnalysisResult(List<Competency> competencies, List<Question> questions) {

  private static final Logger log = LoggerFactory.getLogger(PostingAnalysisResult.class);

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
   * AI가 규칙을 조금 어겨도 쓸 수 있는 것은 살린다(17·30번 #61·#65와 같은 방식). 실패시키지 않는다.
   *
   * <ul>
   *   <li>공고 역량: null·목록 밖 id는 버리고, 같은 id는 처음 것만 쓰고, 5개가 넘으면 앞에서 5개만 쓴다(프롬프트가 중요한 순서로 쓰게 함)
   *   <li>evidenceQuote가 공고 원문에 그대로 없으면 역량은 살리고 evidenceQuote만 null로 바꾼다. 칸 이름을 떼야 있으면 뗀 문장으로 둔다
   *   <li>문항: seq가 범위 밖이면 버린다. 문항 역량은 목록 밖 id만 버리고, 3개가 넘으면 앞에서 3개만 쓴다
   * </ul>
   *
   * 남은 역량이 없어도 실패가 아니다(36번 noCompetencyFound). 버린 것이 있으면 받은 개수 → 쓴 개수를 로그로 남긴다.
   *
   * @param postingText 공고 칸과 첨부 글자를 이은 원문
   */
  public PostingAnalysisResult validate(
      Set<Long> allowedCompetencyIds, int questionCount, String postingText) {
    List<Competency> found = competencies == null ? List.of() : competencies;
    List<Question> perQuestion = questions == null ? List.of() : questions;

    Set<Long> seen = new HashSet<>();
    List<Competency> checked =
        found.stream()
            .filter(c -> c != null && allowedCompetencyIds.contains(c.competencyId()))
            .filter(c -> seen.add(c.competencyId()))
            .limit(MAX_POSTING_COMPETENCIES)
            .map(
                c ->
                    new Competency(
                        c.competencyId(),
                        EvidenceVerifier.verified(postingText, c.evidenceQuote())))
            .toList();

    List<Question> checkedQuestions =
        perQuestion.stream()
            .filter(q -> q != null && q.seq() >= 1 && q.seq() <= questionCount)
            .map(q -> new Question(q.seq(), usableIds(q.competencyIds(), allowedCompetencyIds)))
            .toList();

    int receivedQuestionIds = perQuestion.stream().mapToInt(PostingAnalysisResult::idCount).sum();
    int usedQuestionIds = checkedQuestions.stream().mapToInt(PostingAnalysisResult::idCount).sum();
    if (checked.size() < found.size()
        || checkedQuestions.size() < perQuestion.size()
        || usedQuestionIds < receivedQuestionIds) {
      log.warn(
          "36번 AI 응답 일부를 버림 공고 역량 {}개 → {}개, 문항 {}개 → {}개, 문항 역량 {}개 → {}개",
          found.size(),
          checked.size(),
          perQuestion.size(),
          checkedQuestions.size(),
          receivedQuestionIds,
          usedQuestionIds);
    }
    return new PostingAnalysisResult(checked, checkedQuestions);
  }

  private static List<Long> usableIds(List<Long> ids, Set<Long> allowedCompetencyIds) {
    if (ids == null) {
      return List.of();
    }
    return ids.stream()
        .filter(id -> id != null && allowedCompetencyIds.contains(id))
        .distinct()
        .limit(MAX_QUESTION_COMPETENCIES)
        .toList();
  }

  private static int idCount(Question q) {
    return q == null || q.competencyIds() == null ? 0 : q.competencyIds().size();
  }
}
