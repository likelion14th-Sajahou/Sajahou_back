package com.sajarok.domain.matching.ai;

import com.sajarok.domain.matching.entity.AnswerDraft.Source;
import com.sajarok.domain.storycard.dto.CardSnapshot;
import java.util.List;
import java.util.Map;

/**
 * 자소서 매칭(36·37번) 프롬프트를 한곳에 모은다 (평가 기준: 프롬프트를 서비스 로직에 흩어 두지 않음).
 *
 * <p>호출은 AI 모듈(infra/ai)의 AiClient.call(system, user, 결과 record)로 한다. TODO(AI 모듈 담당): AiClient가 생기면
 * 36·37번 서비스에서 이 문자열로 부른다 (트랜잭션 밖에서).
 */
public final class MatchingPrompts {

  private MatchingPrompts() {}

  /** 프롬프트에 넣는 역량 하나. 목록은 competency 테이블에서 받는다(하드코딩하지 않음, C-06). */
  public record CompetencyOption(Long id, String name) {}

  /** 프롬프트에 넣는 공고 내용. */
  public record PostingText(
      String company,
      String department,
      String postingText,
      String jd,
      String preferredQualification,
      String companyInfo) {}

  // ---- 36번: 공고 분석 ----

  public static final String POSTING_ANALYSIS_SYSTEM =
      """
      너는 채용 공고를 읽고 지원자에게 필요한 역량을 찾는 도우미다.
      규칙:
      1. 역량은 반드시 [역량 목록]의 id 중에서만 고른다. 목록에 없는 역량을 만들지 않는다.
      2. 공고가 원하는 역량은 최대 %d개. 근거가 약하면 덜 고르고, 하나도 없으면 빈 배열로 둔다.
      3. evidenceQuote는 [공고]에 있는 문장을 한 글자도 바꾸지 않고 그대로 옮긴다. 요약하거나 고쳐 쓰지 않는다.
      4. 문항마다 그 문항이 묻는 역량을 최대 %d개 고른다. seq는 [문항]의 번호를 그대로 쓴다.
      5. 아래 JSON 형식으로만 답한다. 다른 글은 쓰지 않는다.
      {"competencies":[{"competencyId":3,"evidenceQuote":"..."}],"questions":[{"seq":1,"competencyIds":[3]}]}
      """
          .formatted(
              PostingAnalysisResult.MAX_POSTING_COMPETENCIES,
              PostingAnalysisResult.MAX_QUESTION_COMPETENCIES);

  /**
   * @param questions 문항 내용 (순서대로, 번호는 1부터)
   */
  public static String postingAnalysisUser(
      PostingText posting, List<CompetencyOption> competencies, List<String> questions) {
    StringBuilder sb = new StringBuilder();
    sb.append("[역량 목록]\n");
    competencies.forEach(
        c -> sb.append("- id ").append(c.id()).append(": ").append(c.name()).append('\n'));
    sb.append("\n[공고]\n").append(postingBody(posting));
    sb.append("\n[문항]\n");
    for (int i = 0; i < questions.size(); i++) {
      sb.append(i + 1).append(". ").append(questions.get(i)).append('\n');
    }
    return sb.toString();
  }

  // ---- 37번: 답 초안 ----

  public static final String ANSWER_DRAFT_SYSTEM =
      """
      너는 지원자의 실제 경험 카드로 자기소개서 답 초안을 쓰는 도우미다.
      규칙:
      1. [출처]에 있는 사실만 쓴다. 출처에 없는 경험·숫자·성과를 지어내지 않는다.
      2. 문장마다 그 내용이 나온 출처 번호(source)를 단다. 지원 공고 출처는 회사·직무와 연결하는 문장에만 쓴다.
      3. 꼭 필요한데 출처에 없는 내용(예: 결과 숫자, 받은 반응)은 지어내지 말고 빈칸 조각으로 둔다.
         빈칸은 slot(situation / action / reason / result 중 하나)과 지원자에게 보여 줄 hint를 쓴다.
      4. 한국어 존댓말 자기소개서 문체로, 글자 수 제한이 있으면 공백 포함 그 안에서 쓴다.
      5. 아래 JSON 형식으로만 답한다. 다른 글은 쓰지 않는다.
      {"segments":[{"type":"text","text":"...","source":1},{"type":"blank","slot":"result","hint":"결과 · 숫자나 받은 반응을 채워 주세요"}]}
      """;

  /**
   * @param sources 출처 목록 (CardRecommender.Recommendation.sources)
   * @param cards 카드 ID → 카드 (출처의 카드 내용을 채울 때 씀)
   */
  public static String answerDraftUser(
      String question,
      Integer charLimit,
      List<Source> sources,
      Map<Long, CardSnapshot> cards,
      PostingText posting) {
    StringBuilder sb = new StringBuilder();
    sb.append("[문항]\n").append(question).append('\n');
    sb.append("글자 수 제한: ").append(charLimit == null ? "없음" : charLimit + "자").append("\n\n");
    sb.append("[출처]\n");
    for (Source source : sources) {
      sb.append("(").append(source.no()).append(") ");
      if ("CARD".equals(source.kind())) {
        sb.append(cardBody(cards.get(source.cardId())));
      } else {
        sb.append("지원 공고\n").append(postingBody(posting));
      }
      sb.append('\n');
    }
    return sb.toString();
  }

  private static String cardBody(CardSnapshot card) {
    StringBuilder sb = new StringBuilder("경험 카드\n");
    line(sb, "제목", card.title());
    line(sb, "상황", card.situation());
    line(sb, "행동", card.action());
    line(sb, "이유", card.reason());
    line(sb, "결과", card.result());
    line(sb, "배운 점", card.learned());
    return sb.toString();
  }

  private static String postingBody(PostingText p) {
    StringBuilder sb = new StringBuilder();
    line(sb, "회사", p.company());
    line(sb, "부서", p.department());
    line(sb, "공고 본문", p.postingText());
    line(sb, "주요 업무·자격 요건", p.jd());
    line(sb, "우대사항", p.preferredQualification());
    line(sb, "기업 정보·인재상", p.companyInfo());
    // TODO(첨부 담당): 공고 첨부 텍스트(attachmentService.readTexts)도 붙인다.
    return sb.toString();
  }

  private static void line(StringBuilder sb, String label, String value) {
    if (value != null && !value.isBlank()) {
      sb.append(label).append(": ").append(value).append('\n');
    }
  }
}
