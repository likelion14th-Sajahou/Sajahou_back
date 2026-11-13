package com.sajarok.domain.retrospective.ai;

import com.sajarok.domain.question.dto.QuestionView;
import com.sajarok.domain.retrospective.entity.AssignmentMode;
import com.sajarok.domain.retrospective.entity.Retrospective;
import com.sajarok.domain.retrospective.entity.RetrospectiveType;
import com.sajarok.domain.user.entity.Part;
import com.sajarok.infra.ai.AttachmentBudget;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** 회고 AI(17·19번) 프롬프트. 호출은 RetroQuestionGenerator·RetroReviewGenerator에서 트랜잭션 밖에서 한다. */
public final class RetroPrompts {

  private RetroPrompts() {}

  /** 17번 system 프롬프트 초안. 태그는 ERD ai_question.tag, 파트별 질문 세트는 WBS BE2-08. TODO: 실제 응답 보며 다듬기. */
  public static final String QUESTIONS_SYSTEM =
      """
      너는 멋쟁이사자처럼 아기사자(교육생)가 쓴 주간 회고 자료와 첨부를 읽고, 회고를 더 구체적으로 쓸 수 있게 질문하는 도우미다.

      [규칙]
      1. 질문은 1 ~ %d개. tag는 [태그 목록]의 이름을 그대로 쓴다: %s. 같은 태그를 여러 번 써도 된다.
      2. 질문마다 [회고 자료]나 [첨부]의 특정 문장을 짚고 묻는다.
         그 문장을 quote에 한 글자도 바꾸지 않고 옮긴다. 첨부에서 옮겼으면 sourceAttachmentId에 그 첨부 번호를, 회고 자료면 null을 쓴다.
      3. 질문마다 행동(내가 한 일)·숫자·결정(무엇을 왜 골랐는지)·느낌(배운 점, 다음에 다르게 할 점) 중 하나만 묻는다.
      4. 자료에 없는 사실을 질문에 전제하지 않는다. "잘 해결했는데", "속도가 2배 빨라졌는데"처럼 자료에 없는 결과를 깔고 묻지 않는다.
      5. 태그 고르는 기준:
         - 근거와 숫자: 결과를 숫자·로그·화면처럼 확인할 수 있는 근거로 말하게 한다. 항상 하나 넣는다.
         - 빠진 칸: 실습·과제·메모 중 비었거나 "했다"처럼 너무 짧은 곳을 채우게 한다.
         - 세션과 연결: 세션 자료(SESSION_PDF)의 개념을 내 실습·과제에 어떻게 썼는지 묻는다. 세션 자료가 없으면 쓰지 않는다.
         - 역량 신호: 문제 해결·협업·설계처럼 자기소개서에 쓸 만한 행동을 구체적으로 꺼내게 한다.
         - 느낀 점: 배운 점이나 다음에 다르게 할 점을 묻는다. 최대 하나.
      6. [파트]에 맞춰 묻는다:
         - 백엔드: API·DB·쿼리·성능처럼 서버에서 한 일과 그렇게 한 이유
         - 프론트엔드: 화면·상태 관리·사용자 동작. [회고 자료]에 코드 리뷰가 있으면 리뷰를 받고 무엇을 고쳤는지
         - 기획·디자인: 사용자·화면 흐름·결정의 근거와 팀 반응
         - 모름: 파트를 가정하지 않는다.
      7. 이미 자세히 쓴 내용은 다시 묻지 않는다. 근거와 숫자는 예외로 항상 묻는다.
      8. 한국어 존댓말, 짧은 두 문장까지.

      [좋은 예]
      입력:
        [파트] 백엔드
        [회고 자료] 실습: 게시글 목록 API에서 N+1 문제를 fetch join으로 해결
                    메모: 이번 주 고민은 페이징과 fetch join을 같이 쓰는 것
        [첨부] (502) FILE / SESSION_PDF / 7주차 JPA.pdf: 지연 로딩과 N+1 문제, fetch join, 배치 사이즈
      출력:
      {"insufficientMaterial":false,"questions":[
        {"tag":"근거와 숫자","quote":"게시글 목록 API에서 N+1 문제를 fetch join으로 해결","sourceAttachmentId":null,
         "content":"고치기 전후로 쿼리 수가 실제로 얼마나 달라졌나요? 로그로 확인한 숫자가 있으면 알려 주세요."},
        {"tag":"세션과 연결","quote":"지연 로딩과 N+1 문제, fetch join, 배치 사이즈","sourceAttachmentId":502,
         "content":"세션에서 배운 방법 중 배치 사이즈 대신 fetch join을 고른 이유가 있나요?"},
        {"tag":"역량 신호","quote":"이번 주 고민은 페이징과 fetch join을 같이 쓰는 것","sourceAttachmentId":null,
         "content":"페이징과 fetch join을 같이 쓸 때 어떤 문제가 있었고, 어떻게 풀었어요?"}
      ]}

      [나쁜 예]
      {"tag":"근거와 숫자","quote":"응답 속도가 3배 빨라짐","sourceAttachmentId":null,"content":"3배 빨라졌다고 했는데 어떻게 측정했어요?"}
      → 자료에 "3배"가 없다. quote를 지어내거나 질문에 없는 사실을 넣으면 안 된다.
      {"tag":"느낀 점","quote":null,"sourceAttachmentId":null,"content":"이번 주에 무엇을 배웠나요?"}
      → 뻔한 질문이다. 어떤 문장도 짚지 않고, 무엇을 묻는지 흐리다.

      [애매할 때]
      - 자료가 "실습함"처럼 너무 짧고 첨부도 없어 물을 거리가 없으면 질문하지 않는다: {"insufficientMaterial":true,"questions":[]}
      - 비어 있는 것을 물을 때는 짚을 문장이 없으므로 quote와 sourceAttachmentId를 null로 두고, 무엇이 비었는지 묻는다.
      - 근거 문장을 원문에서 그대로 찾을 수 없으면 quote와 sourceAttachmentId를 null로 둔다(문장을 고쳐 쓰지 않는다).

      [출력 형식]
      아래 JSON으로만 답한다. 다른 글은 쓰지 않는다.
      - insufficientMaterial: 물을 거리가 없으면 true(이때 questions는 빈 배열)
      - tag: [태그 목록] 중 하나
      - quote: 질문이 짚은 원문 문장 그대로. 없으면 null
      - sourceAttachmentId: quote가 나온 첨부 번호. 회고 자료에서 나왔거나 quote가 null이면 null
      - content: 아기사자에게 보여 줄 질문
      {"insufficientMaterial":false,"questions":[{"tag":"근거와 숫자","quote":"...","sourceAttachmentId":null,"content":"..."}]}
      """
          .formatted(
              RetroQuestionResult.MAX_QUESTIONS, String.join(" / ", RetroQuestionResult.TAGS));

  /**
   * 17번 user 프롬프트.
   *
   * @param part 회원 파트. 모르면 null
   * @param attachments 추출이 끝난(DONE) 첨부
   */
  public static String questionsUser(
      Retrospective r, Part part, List<RetroAttachment> attachments) {
    StringBuilder sb = new StringBuilder();
    sb.append("[태그 목록]\n").append(String.join(" / ", RetroQuestionResult.TAGS)).append("\n\n");
    appendHeader(sb, r, part);
    appendMaterials(sb, r);
    appendAttachments(sb, attachments);
    return sb.toString();
  }

  /** 출처 첨부가 없는 quote를 대조할 원문: 실습·과제·코드 리뷰·메모를 이은 글. */
  public static String materialText(Retrospective r) {
    return Stream.of(
            r.getPracticeContent(), r.getAssignmentContent(), r.getCodeReview(), r.getMemo())
        .filter(v -> v != null && !v.isBlank())
        .collect(Collectors.joining("\n"));
  }

  /** 19번 system 프롬프트 초안. 카드 5칸(상황·행동·이유·결과·배운 점)은 22번 카드 상세와 같다. TODO: 실제 응답 보며 다듬기. */
  public static final String REVIEW_SYSTEM =
      """
      너는 아기사자(교육생)가 쓴 주간 회고 자료·AI 질문에 한 답·첨부를 읽고, 회고록과 스토리 카드 초안을 써 주는 도우미다.

      [규칙]
      1. 자료에 없는 사실·숫자·결과는 쓰지 않는다. 숫자·결과·반응은 [회고 자료]·[질문과 답]·[첨부]에 있는 것만 쓴다.
      2. 카드 칸은 5개다. 근거가 없는 칸은 null로 둔다. 비슷한 내용으로 억지로 채우지 않는다.
         - situation(상황): 어떤 문제나 과제를 마주했는지
         - action(행동): 내가 직접 한 일
         - reason(이유): 그 방법을 고른 이유, 다른 방법과 비교한 것
         - result(결과): 숫자·확인한 결과·받은 반응
         - learned(배운 점): 배운 것과 다음에 다르게 할 것
      3. 칸마다 1~3문장, 아기사자가 직접 쓴 것처럼 1인칭 평서문("~했다")으로 쓴다.
      4. markdownBody는 노션에 붙여 넣을 회고록 본문이다. 아래 ## 제목 중 자료가 있는 것만 이 순서로 쓴다. 맨 위 # 제목은 쓰지 않는다.
         ## 세션 내용 정리 (세션 자료(SESSION_PDF)가 있을 때만. 핵심 개념 3~5개를 bullet로)
         ## 실습
         ## 과제
         ## 고민과 해결
         ## 배운 점
         ## 다음에 할 일
      5. evidence에는 카드 칸마다 근거가 된 원문 문장을 넣는다. quote는 원문을 한 글자도 바꾸지 않고 옮긴다.
         첨부에서 옮겼으면 attachmentId에 그 첨부 번호를, 회고 자료나 질문 답에서 옮겼으면 null을 쓴다. 근거가 없는 칸은 넣지 않는다.
      6. 카드 제목은 서버가 세션명으로 붙이므로 title은 쓰지 않는다.
      7. 한국어로 쓴다.

      [좋은 예]
      입력:
        [회고 자료] 실습: 게시글 목록 API에서 N+1 문제를 fetch join으로 해결
        [질문과 답] Q1. (근거와 숫자) 고치기 전후로 쿼리 수가 얼마나 달라졌나요?
                    A1. 게시글 20개 기준으로 쿼리 21개가 2개로 줄었어요
        [첨부] (502) FILE / SESSION_PDF / 7주차 JPA.pdf: 지연 로딩과 N+1 문제, fetch join, 배치 사이즈
      출력:
      {"situation":"게시글 목록 API에서 N+1 문제가 생겼다.",
       "action":"fetch join으로 게시글과 작성자를 한 번에 가져오게 바꿨다.",
       "reason":null,
       "result":"게시글 20개 기준으로 쿼리가 21개에서 2개로 줄었다.",
       "learned":null,
       "markdownBody":"## 세션 내용 정리\\n- 지연 로딩과 N+1 문제\\n- fetch join\\n- 배치 사이즈\\n\\n## 실습\\n게시글 목록 API에서 N+1 문제를 fetch join으로 해결했다. 게시글 20개 기준으로 쿼리가 21개에서 2개로 줄었다.",
       "evidence":{
         "situation":[{"attachmentId":null,"quote":"게시글 목록 API에서 N+1 문제를 fetch join으로 해결"}],
         "result":[{"attachmentId":null,"quote":"게시글 20개 기준으로 쿼리 21개가 2개로 줄었어요"}]}}
      → 왜 fetch join을 골랐는지와 배운 점은 자료에 없어서 reason·learned를 null로 둔다.

      [나쁜 예]
      "result":"쿼리가 90% 줄어 응답 속도가 빨라졌다" → 자료에 "90%"나 응답 속도가 없다.
      "reason":"유지보수가 쉬워서 fetch join을 골랐다" → 자료에 없는 이유를 지어냈다. null로 둔다.

      [출력 형식]
      아래 JSON으로만 답한다. 다른 글은 쓰지 않는다. 근거가 없는 칸은 null.
      {"situation":"...","action":"...","reason":null,"result":"...","learned":"...",
       "markdownBody":"## 실습\\n...",
       "evidence":{"situation":[{"attachmentId":null,"quote":"..."}],"result":[{"attachmentId":502,"quote":"..."}]}}
      """;

  /**
   * 19번 user 프롬프트.
   *
   * @param part 회원 파트. 모르면 null
   * @param attachments 추출이 끝난(DONE) 첨부
   * @param answered 답한(ANSWERED) 질문
   */
  public static String reviewUser(
      Retrospective r, Part part, List<RetroAttachment> attachments, List<QuestionView> answered) {
    StringBuilder sb = new StringBuilder();
    appendHeader(sb, r, part);
    appendMaterials(sb, r);
    sb.append("\n[질문과 답]\n");
    if (answered.isEmpty()) {
      sb.append("없음\n");
    }
    for (int i = 0; i < answered.size(); i++) {
      QuestionView q = answered.get(i);
      sb.append("Q").append(i + 1).append(". (").append(q.tag()).append(") ").append(q.content());
      sb.append("\nA")
          .append(i + 1)
          .append(". ")
          .append(q.answer() == null ? "" : q.answer().strip());
      sb.append('\n');
    }
    appendAttachments(sb, attachments);
    return sb.toString();
  }

  /** 19번: 출처 첨부가 없는 evidence quote를 대조할 원문. 회고 자료에 질문 답을 더한다. */
  public static String reviewSourceText(Retrospective r, List<QuestionView> answered) {
    String answers =
        answered.stream()
            .map(QuestionView::answer)
            .filter(v -> v != null && !v.isBlank())
            .collect(Collectors.joining("\n"));
    return materialText(r) + "\n" + answers;
  }

  /**
   * 19번 회고록 전문: 서버가 # 제목("10주차 세션 회고")을 붙인다.
   *
   * @param weekNumber 주차 번호. 모르면 null(제목에서 주차를 뺀다)
   */
  public static String fullMarkdown(Retrospective r, Integer weekNumber, String markdownBody) {
    return "# " + heading(r, weekNumber) + "\n\n" + markdownBody.strip() + "\n";
  }

  /**
   * 19번 카드 제목: 그 주차 내 파트의 세션명(예: "JPA 연관관계"). 세션명을 모르면(파트·일정 없음) 회고록 제목("10주차 세션 회고")을 쓴다.
   *
   * @param sessionTitle 그 주차 내 파트의 세션명. 모르면 null
   */
  public static String cardTitle(Retrospective r, Integer weekNumber, String sessionTitle) {
    if (sessionTitle != null && !sessionTitle.isBlank()) {
      return sessionTitle.strip();
    }
    return heading(r, weekNumber);
  }

  private static String heading(Retrospective r, Integer weekNumber) {
    String type = r.getType() == RetrospectiveType.ASSIGNMENT ? "과제 회고" : "세션 회고";
    return weekNumber == null ? type : weekNumber + "주차 " + type;
  }

  private static void appendHeader(StringBuilder sb, Retrospective r, Part part) {
    sb.append("[회고 종류] ")
        .append(r.getType() == RetrospectiveType.ASSIGNMENT ? "과제 회고" : "세션 회고")
        .append('\n');
    sb.append("[파트] ").append(partLabel(part)).append("\n\n");
    // TODO(주차 담당): 주차 모듈이 생기면 [세션] 주차·세션 제목을 넣는다.
  }

  private static void appendMaterials(StringBuilder sb, Retrospective r) {
    sb.append("[회고 자료]\n");
    int before = sb.length();
    if (Boolean.FALSE.equals(r.getHasPractice())) {
      sb.append("실습: 안 함\n");
    } else {
      line(sb, "실습", r.getPracticeContent());
    }
    if (r.getAssignmentMode() == AssignmentMode.LATER) {
      sb.append("과제: 과제 회고에서 따로 씀\n");
    } else if (r.getAssignmentMode() == AssignmentMode.NONE) {
      sb.append("과제: 없음\n");
    } else {
      line(sb, "과제", r.getAssignmentContent());
    }
    line(sb, "코드 리뷰", r.getCodeReview());
    line(sb, "메모", r.getMemo());
    if (sb.length() == before) {
      sb.append("없음\n");
    }
  }

  /** 첨부는 다 합쳐 AttachmentBudget.TOTAL_CHARS(4만 자)까지 넣는다. 인용 검증은 자르지 않은 전체 글로 한다. */
  private static void appendAttachments(StringBuilder sb, List<RetroAttachment> attachments) {
    sb.append("\n[첨부]\n");
    if (attachments.isEmpty()) {
      sb.append("없음\n");
    }
    List<String> texts =
        AttachmentBudget.fit(attachments.stream().map(RetroAttachment::text).toList());
    for (int i = 0; i < attachments.size(); i++) {
      RetroAttachment a = attachments.get(i);
      sb.append("(")
          .append(a.id())
          .append(") ")
          .append(a.type())
          .append(" / ")
          .append(a.category())
          .append(" / ")
          .append(a.name())
          .append(": ")
          .append(texts.get(i))
          .append('\n');
    }
  }

  private static String partLabel(Part part) {
    if (part == null) {
      return "모름";
    }
    return switch (part) {
      case BACKEND -> "백엔드";
      case FRONTEND -> "프론트엔드";
      case PLAN_DESIGN -> "기획·디자인";
    };
  }

  private static void line(StringBuilder sb, String label, String value) {
    if (value != null && !value.isBlank()) {
      sb.append(label).append(": ").append(value.strip()).append('\n');
    }
  }
}
