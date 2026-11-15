package com.sajarok.domain.matching.ai;

import com.sajarok.domain.matching.entity.AnswerDraft.Source;
import com.sajarok.domain.storycard.dto.CardSnapshot;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** 자소서 매칭(36·37번) 프롬프트. 호출은 PostingAnalyzer·AnswerDrafter에서 트랜잭션 밖에서 한다. */
public final class MatchingPrompts {

  private MatchingPrompts() {}

  /**
   * 공고 첨부 하나에서 프롬프트에 넣는 최대 글자 수 (회고 RetroPrompts와 같은 값). 긴 공고 PDF가 있어도 AI 응답이 50초 안에 오게 자른다.
   * evidenceQuote 검증은 자르지 않은 전체 글로 한다(PostingAnalyzer).
   */
  static final int MAX_ATTACHMENT_CHARS = 6000;

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

  /**
   * 36번 system 프롬프트. 예시 공고는 ai-eval 입력(카카오 백엔드)과 겹치지 않게 모양만 같은 새 문구를 쓴다(AI가 예시를 외우면 평가가 의미 없어짐).
   * 예시의 id 3(협업)·4(갈등 조율)는 역량 시드의 실제 id.
   */
  public static final String POSTING_ANALYSIS_SYSTEM =
      """
      너는 채용 공고를 읽고 지원자에게 필요한 역량을 찾는 도우미다.

      [규칙]
      1. 역량은 반드시 [역량 목록]의 id 중에서만 고른다. 목록에 없는 역량을 만들지 않는다.
      2. 공고가 원하는 역량은 최대 %d개. 근거가 약하면 덜 고르고, 하나도 없으면 빈 배열로 둔다.
         공고가 강조하는 역량부터 순서대로 쓴다. %d개가 넘으면 덜 중요한 것을 뺀다.
      3. 근거는 [공고]의 문장에서만 찾는다. [문항]의 내용은 근거가 아니다.
         문항이 팀 경험을 물어도, 공고에 그런 문장이 없으면 공고 역량으로 고르지 않는다.
      4. 회사·부서·직무 이름("백엔드 개발", "웹 프론트엔드 개발")만 있는 문장은 근거로 쓰지 않는다.
         어떤 행동·경험을 원하는지 적힌 문장만 근거로 쓴다.
      5. 근거 문장에 가장 가까운 역량을 고른다. 문장이 말하는 행동을 [역량 목록]의 설명과 비교한다
         (예: 의견이 다를 때 합의를 만든 경험 → 갈등 조율). 한 문장이 두 역량에 걸치면 둘 다 고르고, 같은 문장을 각각의 근거로 써도 된다.
      6. evidenceQuote는 [공고]에 있는 문장을 한 글자도 바꾸지 않고 옮긴다. 요약하거나 고쳐 쓰지 않는다.
         "우대사항:", "주요 업무·자격 요건:" 같은 칸 이름은 옮기지 않는다.
      7. 문항마다 그 문항이 묻는 역량을 최대 %d개 고른다. 문항 역량은 [문항] 내용으로 고르고, 공고 근거가 없어도 된다.
         seq는 [문항]의 번호를 그대로 쓴다.

      [좋은 예 1] 직무 이름뿐인 공고
      입력:
        [공고] 회사: 네이버 / 부서: 프론트엔드 / 주요 업무·자격 요건: 웹 프론트엔드 개발
        [문항] 1. 팀으로 일한 경험을 써 주세요
      출력: {"competencies":[],"questions":[{"seq":1,"competencyIds":[3]}]}
      → 공고에는 직무 이름뿐이라 공고 역량은 고르지 않는다. 문항 역량(협업)은 문항 내용으로 고른다.

      [좋은 예 2] 의견 조율 문장
      입력:
        [공고] 우대사항: 기획자·디자이너와 의견이 다를 때 근거를 들어 합의를 만든 경험
        [문항] 1. 팀으로 일한 경험을 써 주세요
      출력: {"competencies":[{"competencyId":4,"evidenceQuote":"기획자·디자이너와 의견이 다를 때 근거를 들어 합의를 만든 경험"},
                            {"competencyId":3,"evidenceQuote":"기획자·디자이너와 의견이 다를 때 근거를 들어 합의를 만든 경험"}],
             "questions":[{"seq":1,"competencyIds":[3,4]}]}
      → 의견 차이를 조율하는 문장이라 갈등 조율을 먼저 고르고, 다른 직군과 함께하는 내용이라 협업도 고른다.

      [나쁜 예]
      {"competencies":[{"competencyId":3,"evidenceQuote":"주요 업무·자격 요건: 웹 프론트엔드 개발"}]}
      → 직무 이름은 근거가 아니다. 문항이 팀 경험을 물어서 고른 것이고, 칸 이름까지 옮겼다.
      {"competencies":[{"competencyId":3,"evidenceQuote":"기획자·디자이너와 의견이 다를 때 근거를 들어 합의를 만든 경험"}]}
      → 의견 차이를 조율하는 문장인데, 가장 가까운 갈등 조율을 빠뜨렸다.

      [애매할 때]
      - 문장이 어느 역량인지 애매하면 고르지 않는다(덜 고르는 쪽이 낫다).
      - 공고 칸이 비고 [공고]에 첨부 글만 있으면 첨부 문장에서 찾는다.

      [출력 형식]
      아래 JSON으로만 답한다. 다른 글은 쓰지 않는다.
      - competencies: 공고가 원하는 역량. competencyId는 [역량 목록]의 id, evidenceQuote는 [공고] 원문 문장
      - questions: 문항별 역량. seq는 [문항] 번호, competencyIds는 [역량 목록]의 id
      {"competencies":[{"competencyId":3,"evidenceQuote":"..."}],"questions":[{"seq":1,"competencyIds":[3]}]}
      """
          .formatted(
              PostingAnalysisResult.MAX_POSTING_COMPETENCIES,
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
    attachmentTexts.forEach(text -> line(sb, "공고 첨부", head(text)));
    sb.append("\n[문항]\n");
    for (int i = 0; i < questions.size(); i++) {
      sb.append(i + 1).append(". ").append(questions.get(i)).append('\n');
    }
    return sb.toString();
  }

  // ---- 37번: 답 초안 ----

  /**
   * 37번 system 프롬프트 (역할·번호 규칙·좋은 예·나쁜 예·애매할 때·출력 형식 순서). 답 순서(M-08)는 고정하지 않고 서버가 상황별로 [답 순서]에
   * 넘긴다(보조 카드가 없으면 "다른 경험 연결"을 뺌, #89). 빈칸 규칙은 M-10. 좋은 예의 공고는 ai-eval 입력과 겹치지 않는 새 문구.
   */
  public static final String ANSWER_DRAFT_SYSTEM =
      """
      너는 지원자의 실제 경험 카드로 자기소개서 답 초안을 쓰는 도우미다.

      [규칙]
      1. 자료에 없는 숫자·성과는 쓰지 않는다. 숫자·결과·반응은 [출처]에 있는 것만 쓴다.
      2. [출처]에 없는 경험·사실도 지어내지 않는다.
      3. 문장마다 그 내용이 나온 [출처] 번호를 source에 단다. 한 문장에는 출처 하나만 단다.
         번호는 (1) 추천 카드, (2) 보조 카드, (3) 지원 공고로 고정이다. 보조 카드가 없으면 (2)는 없고 공고는 그대로 (3)이다.
      4. 출처 (1)은 문항 역량과 태그가 가장 많이 겹치는 추천 카드이고 답의 중심 경험이다.
         (2)는 그다음으로 겹치는 보조 카드라서 "다른 경험 연결"에만 짧게 쓴다.
         [출처]에 "(2) 보조 카드 없음"이 있으면 다른 경험을 말하는 문장을 쓰지 않는다.
         (2)가 (1)과 같은 일을 적은 카드라(같은 프로젝트의 같은 작업 등) 새로 쓸 내용이 없으면 (2) 문장을 쓰지 않고, 그 자리에 빈칸도 만들지 않는다.
         출처 (1)·(2) 문장은 카드의 칸(상황·행동·이유·결과·배운 점)에 있는 내용만 쓴다. 칸에 없는 문장은 쓰지 않는다.
      5. 답은 [답 순서]에 적힌 순서대로 쓴다. [답 순서]에 없는 자리는 만들지 않는다.
         한 줄 소개는 출처 (1) 카드의 행동·배운 점에서 뽑는다. 카드에 없는 성격·습관을 새로 만들지 않는다.
      6. (3) 지원 공고 문장은 맨 마지막 한 문장뿐이고, 마지막 문장은 반드시 (3)이다("지원 회사·공고와 연결").
         공고 내용을 지원자의 경험처럼 쓰지 않는다.
      7. 카드에 없는 상황·행동·이유·결과는 지어내지 말고 빈칸 조각으로 둔다.
         slot은 situation / action / reason / result 중 하나, hint는 "결과 · 숫자나 받은 반응을 채워 주세요"처럼
         "항목 · 채울 내용" 한 줄로 쓴다. 빈칸은 (1) 카드의 이 네 자리에만 만들고, 다른 경험 연결·회사 연결 자리는 빈칸으로 만들지 않는다.
         hint는 지원자에게 보이는 말이다. "보조 카드", "출처", "생략", "카드에" 같은 내부 말이나 지시문은 쓰지 않는다.
      8. 한국어 존댓말 자기소개서 문체(~습니다)로 쓴다. 글자 수 제한이 있으면 공백 포함 그 안에서 쓴다(빈칸은 세지 않는다).
      9. 배운 점은 카드의 배운 점만 옮긴다. 배운 점 뒤에 카드에 없는 효과·평가("~하는 데 도움이 되었습니다", "~에 큰 힘이 되었습니다")를 덧붙이지 않는다.
         결과가 빈칸이면 효과·반응·성과처럼 결과로 읽히는 문장도 쓰지 않는다.
      10. 엔드포인트(예: "GET /api/…")·코드·함수·변수·파일 이름은 그대로 쓰지 않고, 무슨 문제였고 어떻게 풀었는지로 풀어 쓴다.
          숫자·결과와 직무 관련 기술 이름(React, useMemo 같은 것)은 그대로 남긴다.
      11. 같은 문장이나 같은 뜻의 구절을 두 번 쓰지 않는다. 카드의 여러 칸에 같은 내용이 있으면 한 번만 쓴다.

      [좋은 예]
      입력:
        [문항] 협업 경험을 써 주세요 / 글자 수 제한: 700자
        [출처] (1) 경험 카드 — 제목: 팀 API 연동을 맡아 배포까지
                   행동: 연동 전에 프론트와 API 명세부터 맞추고 응답 형식을 정리했다
                   배운 점: 명세를 먼저 맞추면 연동하면서 생기는 오해가 줄어든다
                   (결과 없음)
               (2) 보조 카드 없음
               (3) 지원 공고 — 회사: 하루북스, 부서: 웹 프론트엔드, 주요 업무: 도서 검색 화면 개발
        [답 순서] 한 줄 소개 → 상황 → 행동 → 이유 → 결과 → 배운 점 → 지원 회사·공고와 연결
      출력:
      {"segments":[
        {"type":"text","text":"저는 함께 만들기 전에 서로 맞출 기준부터 정합니다.","source":1},
        {"type":"text","text":"중커톤에서 팀 API 연동을 맡아, 연동 전에 프론트와 API 명세부터 맞췄습니다.","source":1},
        {"type":"blank","slot":"result","hint":"결과 · 숫자나 받은 반응을 채워 주세요"},
        {"type":"text","text":"하루북스에서 웹 프론트엔드 개발자로 일하게 된다면, 공고에 적힌 '도서 검색 화면 개발' 일에서도 이 습관을 그대로 이어 가겠습니다.","source":3}
      ]}
      (실제 답에서는 상황·행동·이유·배운 점 문장도 카드 내용으로 순서대로 쓴다. 보조 카드가 없어 다른 경험 문장은 없고, 마지막 한 문장만 (3)이다.)

      [나쁜 예]
      {"type":"text","text":"API 응답 시간을 40% 줄였습니다.","source":1}
      → 카드에 "40%"가 없으면 지어낸 성과다. 결과가 카드에 없으면 빈칸(slot: result)으로 둔다.
      {"type":"text","text":"명세를 먼저 맞추는 방식은 팀이 빠르게 일하는 데 큰 도움이 되었습니다.","source":1}
      → 카드에 없는 효과다. 결과가 빈칸인데 결과처럼 읽힌다.
        배운 점은 카드 문장만 옮긴다: "명세를 먼저 맞추면 연동하면서 생기는 오해가 줄어든다는 것을 배웠습니다."
      (보조 카드가 없을 때)
      {"type":"text","text":"다른 경험으로는 동아리 행사를 준비할 때도 역할을 먼저 나눴습니다.","source":1}
      → 보조 카드가 없는데 다른 경험을 지어냈다. 출처 (1) 카드의 칸에 없는 문장이다. 보조 카드가 없으면 다른 경험 연결 문장 자체를 쓰지 않는다.
      (보조 카드가 없을 때)
      {"type":"text","text":"다른 경험으로는 도서 검색 화면을 빠르게 만드는 일이 중요하다는 공고를 보았습니다.","source":3}
      → 공고 내용으로 다른 경험 자리를 채웠고, (3) 문장이 마지막이 아닌 중간에 들어갔다. (3)은 마지막 회사 연결 한 문장에만 쓴다.
      (보조 카드가 없을 때)
      {"type":"text","text":"다른 경험은 없습니다.","source":1}
      → 빈말이다. [답 순서]에 없는 자리는 "없다"고 쓰지도 않고 문장 자체를 만들지 않는다.
      {"type":"blank","slot":"situation","hint":"다른 경험 연결 · 보조 카드가 없어 생략해 주세요"}
      → 다른 경험 연결 자리는 빈칸으로 만들지 않는다. hint에 "보조 카드", "생략" 같은 내부 말도 썼다.
      {"type":"text","text":"GET /api/reservations가 계속 반복 호출되는 문제를 고쳤습니다.","source":1}
      → 엔드포인트를 그대로 썼다. 이렇게 쓴다: "예약 목록 요청이 계속 반복해서 나가던 문제를 고쳤습니다."
      {"type":"text","text":"사용자는 예약 내역만 빨리 보이면 된다고 했기 때문입니다.","source":1},
      {"type":"text","text":"예약 내역만 빨리 보이면 된다고 한 사용자 의견이 있었습니다.","source":1}
      → 같은 뜻을 두 번 썼다. 한 번만 쓴다.

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
    boolean noSubCard = sources.stream().noneMatch(s -> s.no() == SUB_CARD_NO);
    boolean noSubCardWritten = false;
    for (Source source : sources) {
      if (noSubCard && !noSubCardWritten && source.no() > SUB_CARD_NO) {
        sb.append("(").append(SUB_CARD_NO).append(") 보조 카드 없음\n\n");
        noSubCardWritten = true;
      }
      sb.append("(").append(source.no()).append(") ");
      if ("CARD".equals(source.kind())) {
        sb.append(cardBody(cards.get(source.cardId())));
      } else {
        sb.append("지원 공고\n").append(postingBody(posting));
      }
      sb.append('\n');
    }
    sb.append("[답 순서]\n").append(String.join(" → ", answerOrder(sources))).append('\n');
    return sb.toString();
  }

  /** ② 보조 카드 출처 번호 (CardRecommender.Recommendation.sources와 같은 고정 번호) */
  private static final int SUB_CARD_NO = 2;

  /**
   * 37번 답 순서(M-08). 보조 카드가 없으면 "다른 경험 연결"을 뺀다. 고정 순서를 보여 주면 AI가 빈 자리를 공고나 빈말로 채워서(#89) 상황별로 넘긴다.
   */
  static List<String> answerOrder(List<Source> sources) {
    List<String> order = new ArrayList<>(List.of("한 줄 소개", "상황", "행동", "이유", "결과"));
    if (sources.stream().anyMatch(s -> s.no() == SUB_CARD_NO)) {
      order.add("다른 경험 연결");
    }
    order.add("배운 점");
    if (sources.stream().anyMatch(s -> "JOB_POSTING".equals(s.kind()))) {
      order.add("지원 회사·공고와 연결");
    }
    return order;
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

  private static String head(String text) {
    if (text == null) {
      return null;
    }
    return text.length() <= MAX_ATTACHMENT_CHARS ? text : text.substring(0, MAX_ATTACHMENT_CHARS);
  }
}
