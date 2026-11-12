package com.sajarok.domain.matching.ai;

import com.sajarok.domain.matching.entity.AnswerDraft.Source;
import com.sajarok.domain.storycard.dto.CardSnapshot;
import java.util.List;
import java.util.Map;

/** 자소서 매칭(36·37번) 프롬프트. 호출은 PostingAnalyzer·AnswerDrafter에서 트랜잭션 밖에서 한다. */
public final class MatchingPrompts {

  private MatchingPrompts() {}

  /** 프롬프트에 넣는 역량 하나. 목록은 competency 테이블에서 받는다(하드코딩하지 않음, C-06). */
  public record CompetencyOption(Long id, String name, String description) {}

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
   * @param attachmentTexts 공고 첨부에서 추출한 글자. 공고 원문의 일부라서 [공고] 안에 넣는다(evidenceQuote도 여기서 옮길 수 있게).
   * @param questions 문항 내용 (순서대로, 번호는 1부터)
   */
  public static String postingAnalysisUser(
      PostingText posting,
      List<String> attachmentTexts,
      List<CompetencyOption> competencies,
      List<String> questions) {
    StringBuilder sb = new StringBuilder();
    sb.append("[역량 목록]\n");
    competencies.forEach(
        competency ->
            sb.append("- id ")
                .append(competency.id())
                .append(": ")
                .append(competency.name())
                .append(" — ")
                .append(competency.description())
                .append('\n'));
    ;
    sb.append("\n[공고]\n").append(postingBody(posting));
    attachmentTexts.forEach(text -> line(sb, "공고 첨부", text));
    sb.append("\n[문항]\n");
    for (int i = 0; i < questions.size(); i++) {
      sb.append(i + 1).append(". ").append(questions.get(i)).append('\n');
    }
    return sb.toString();
  }

  // ---- 37번: 답 초안 ----

  /**
   * 37번 system 프롬프트 초안 (역할·번호 규칙·좋은 예·나쁜 예·애매할 때·출력 형식 순서). 답 구성 순서는 M-08, 빈칸 규칙은 M-10, 회사 연결 예시
   * 문장은 프로토타입 문구(한 줄 소개는 예시 카드 내용에서 뽑음).
   *
   * <p>TODO(AI 모듈 연결 후): 실제 응답을 보면서 다듬는다.
   */
  public static final String ANSWER_DRAFT_SYSTEM =
      """
      너는 지원자의 실제 경험 카드로 자기소개서 답 초안을 쓰는 도우미다.

      [규칙]
      1. 자료에 없는 숫자·성과는 쓰지 않는다. 숫자·결과·반응은 [출처]에 있는 것만 쓴다.
      2. [출처]에 없는 경험·사실도 지어내지 않는다.
      3. 문장마다 그 내용이 나온 [출처] 번호를 source에 단다. 한 문장에는 출처 하나만 단다.
      4. 출처 (1)은 문항 역량과 태그가 가장 많이 겹치는 추천 카드이고 답의 중심 경험이다.
         (2)가 경험 카드면 그다음으로 겹치는 보조 카드라서 "다른 경험 연결"에만 짧게 쓴다.
      5. 답은 이 순서로 쓴다: 한 줄 소개 → 상황 → 행동 → 이유 → 결과 → 다른 경험 연결 → 배운 점 → 지원 회사·공고와 연결.
         보조 카드가 없으면 "다른 경험 연결"은 건너뛴다.
         한 줄 소개는 출처 (1) 카드의 행동·배운 점에서 뽑는다. 카드에 없는 성격·습관을 새로 만들지 않는다.
      6. "지원 공고" 출처는 마지막 "지원 회사·공고와 연결" 문장에만 쓴다. 공고 내용을 지원자의 경험처럼 쓰지 않는다.
      7. 카드에 없는 상황·행동·이유·결과는 지어내지 말고 빈칸 조각으로 둔다.
         slot은 situation / action / reason / result 중 하나, hint는 "결과 · 숫자나 받은 반응을 채워 주세요"처럼
         "항목 · 채울 내용" 한 줄로 쓴다.
      8. 한국어 존댓말 자기소개서 문체(~습니다)로 쓴다. 글자 수 제한이 있으면 공백 포함 그 안에서 쓴다(빈칸은 세지 않는다).

      [좋은 예]
      입력:
        [문항] 협업 경험을 써 주세요 / 글자 수 제한: 700자
        [출처] (1) 경험 카드 — 제목: 팀 API 연동을 맡아 배포까지
                   행동: 연동 전에 프론트와 API 명세부터 맞추고 응답 형식을 정리했다
                   배운 점: 명세를 먼저 맞추면 연동하면서 생기는 오해가 줄어든다
                   (결과 없음)
               (2) 지원 공고 — 회사: 카카오, 부서: 백엔드, 주요 업무: 대규모 트래픽 API 개발
      출력:
      {"segments":[
        {"type":"text","text":"저는 함께 만들기 전에 서로 맞출 기준부터 정합니다.","source":1},
        {"type":"text","text":"중커톤에서 팀 API 연동을 맡아, 연동 전에 프론트와 API 명세부터 맞췄습니다.","source":1},
        {"type":"blank","slot":"result","hint":"결과 · 숫자나 받은 반응을 채워 주세요"},
        {"type":"text","text":"카카오에서 백엔드 개발자로 일하게 된다면, 공고에 적힌 '대규모 트래픽 API 개발' 일에서 이 습관을 그대로 이어 가겠습니다.","source":2}
      ]}
      (실제 답에서는 상황·행동·이유·배운 점 문장도 카드 내용으로 순서대로 쓴다.)

      [나쁜 예]
      {"type":"text","text":"API 응답 시간을 40% 줄였습니다.","source":1}
      → 카드에 "40%"가 없으면 지어낸 성과다. 결과가 카드에 없으면 빈칸(slot: result)으로 둔다.

      [애매할 때]
      - 카드 내용이 문항과 잘 맞지 않으면 억지로 늘리지 말고 짧게 쓰고, 부족한 부분은 빈칸으로 둔다.
      - 한 줄 소개나 배운 점을 카드에서 뽑을 수 없으면 그 문장은 쓰지 않는다(빈칸으로도 만들지 않는다).
      - 글자 수 제한 안에 다 못 쓰면 "다른 경험 연결"부터 줄인다.

      [출력 형식]
      아래 JSON으로만 답한다. 다른 글은 쓰지 않는다. segments는 답의 순서대로 쓴다.
      - type: text(문장) 또는 blank(빈칸)
      - text: 문장 (text일 때)
      - source: 그 문장이 나온 [출처] 번호 (text일 때 필수)
      - slot: 빈칸 종류 situation / action / reason / result (blank일 때)
      - hint: 지원자에게 보여 줄 "항목 · 채울 내용" 한 줄 (blank일 때)
      {"segments":[{"type":"text","text":"...","source":1},{"type":"blank","slot":"result","hint":"..."}]}
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
    return sb.toString();
  }

  private static void line(StringBuilder sb, String label, String value) {
    if (value != null && !value.isBlank()) {
      sb.append(label).append(": ").append(value).append('\n');
    }
  }
}
