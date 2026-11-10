package com.sajarok.domain.project.ai;

import com.sajarok.domain.project.entity.Project;
import com.sajarok.domain.project.entity.ProjectRecord;
import com.sajarok.domain.question.dto.QuestionView;
import java.util.List;

/**
 * 프로젝트 마치기(32·33번) 프롬프트를 한곳에 모은다 (프롬프트를 서비스 로직에 흩어 두지 않음).
 *
 * <p>호출은 AI 모듈(infra/ai)의 AiClient.call(system, user, 결과 record)로 한다. TODO(AI 모듈 담당): AiClient가 생기면
 * FinishQuestionGenerator·PortfolioDrafter에서 이 문자열로 부른다 (트랜잭션 밖에서).
 */
public final class FinishPrompts {

  private FinishPrompts() {}

  /**
   * 32번 system 프롬프트 초안 (역할·번호 규칙·좋은 예·나쁜 예·애매할 때·출력 형식 순서). 태그와 예시 문구는 기능명세 H-17과 프로토타입 문구.
   *
   * <p>TODO(AI 모듈 연결 후): 실제 응답을 보면서 다듬는다.
   */
  public static final String QUESTIONS_SYSTEM =
      """
      너는 프로젝트를 마친 아기사자가 포트폴리오를 쓰기 전에, 기록에서 빠진 내용을 물어보는 도우미다.

      [규칙]
      1. 포트폴리오에 필요한 항목은 [태그 목록]의 다섯 가지다: %s
         [기록]을 모두 읽고, 이 중 기록에 내용이 없거나 거의 없는 항목만 질문한다.
      2. 질문은 최대 %d개, 태그마다 하나까지. 기록에 이미 충분히 있는 항목은 묻지 않는다.
      3. tag는 [태그 목록]의 이름을 한 글자도 바꾸지 않고 쓴다.
      4. quote에는 그 항목이 왜 빈 곳인지 한 줄로 쓴다 (예: "결과에 숫자가 거의 없어요"). 화면에 그대로 보인다.
      5. content는 아기사자에게 묻는 질문 한 문장(또는 짧은 두 문장)이다. 한국어 존댓말, 확인할 수 있는 것만 묻는다.
      6. 기록에 없는 사실을 질문이나 quote 안에 넣지 않는다(질문으로 사실을 지어내지 않는다).

      [좋은 예]
      입력:
        [프로젝트] 사자록 · 멋사 회고 서비스 / 역할: BACKEND
        [기록] (1) 2026-11-03 한 일: 팀 API 연동을 맡았다 / 결과·반응: 연동을 마쳤다
               (2) 2026-11-05 한 일: 회고 화면 API를 만들었다 / 고민·해결: 응답 형식을 프론트와 맞췄다
        [첨부] 없음
      출력:
      {"questions":[
        {"tag":"내 역할","quote":"기록마다 역할이 흩어져 있어요","content":"프로젝트 전체에서 내가 맡은 범위를 한 문장으로 말하면요?"},
        {"tag":"최종 결과","quote":"프로젝트 끝의 결과가 기록에 없어요","content":"최종 결과는 어땠어요? 시연, 수상, 사용자 반응처럼 확인할 수 있는 것만요."},
        {"tag":"근거와 숫자","quote":"결과에 숫자가 거의 없어요","content":"숫자로 보여 줄 수 있는 게 있나요? 기간, 인터뷰 인원, 사용자 수처럼요."},
        {"tag":"산출물 요청","quote":"최종 산출물이 아직 첨부되지 않았어요","content":"포트폴리오에 넣을 산출물을 첨부해 주세요. 최종 발표 자료, 시연 링크, 피그마가 있으면 좋아요."},
        {"tag":"배운 점","quote":"프로젝트 전체를 돌아본 문장이 없어요","content":"다음 프로젝트에 꼭 가져갈 한 가지는요?"}
      ]}
      (기록에 최종 발표 자료가 첨부돼 있었다면 "산출물 요청"은 빼고 나머지만 묻는다.)

      [나쁜 예]
      {"tag":"근거와 숫자","quote":"응답 시간 개선 수치가 없어요","content":"응답 시간을 40%% 줄였다고 했는데 어떻게 측정했나요?"}
      → 기록에 "응답 시간"이나 "40%%"가 없다. 기록에 없는 사실을 질문에 넣으면 안 된다.

      [애매할 때]
      - 항목이 기록에 조금 있지만 포트폴리오에 쓰기엔 부족하면 질문한다(예: 결과는 있는데 숫자가 없으면 "근거와 숫자").
      - 다섯 항목이 모두 기록에 충분하면 질문하지 않고 빈 배열로 답한다: {"questions":[]}

      [출력 형식]
      아래 JSON으로만 답한다. 다른 글은 쓰지 않는다.
      {"questions":[{"tag":"산출물 요청","quote":"...","content":"..."}]}
      """
          .formatted(
              String.join(" / ", FinishQuestionResult.TAGS), FinishQuestionResult.MAX_QUESTIONS);

  /**
   * @param records 고른 기록 (만든 순서)
   */
  public static String questionsUser(Project project, List<ProjectRecord> records) {
    StringBuilder sb = new StringBuilder();
    sb.append("[태그 목록]\n").append(String.join(" / ", FinishQuestionResult.TAGS)).append("\n\n");
    appendProject(sb, project);
    appendRecords(sb, records);
    appendAttachments(sb);
    return sb.toString();
  }

  // ---- 33번: 포트폴리오 초안 ----

  /**
   * 33번 system 프롬프트 초안 (역할·번호 규칙·좋은 예·나쁜 예·애매할 때·출력 형식 순서). 항목과 빈칸 기준은 명세 33번·H-18 프로토타입.
   *
   * <p>TODO(AI 모듈 연결 후): 실제 응답을 보면서 다듬는다.
   */
  public static final String PORTFOLIO_DRAFT_SYSTEM =
      """
      너는 프로젝트를 마친 아기사자의 기록과 마치기 답으로 포트폴리오 초안을 쓰는 도우미다.

      [규칙]
      1. [기록]과 [마치기 답]에 있는 사실만 쓴다. 없는 기능·숫자·성과·반응을 지어내지 않는다.
      2. 항목은 [항목 목록]의 6개이고 key를 그대로 쓴다:
         role(내 역할) / contribution(주요 기여) / problem(문제와 해결) / result(결과) / artifact(산출물) / learned(배운 점)
      3. 항목의 근거가 기록과 답에 없으면 body를 null로 둔다. 비슷한 내용으로 채우지 않는다.
      4. 마치기 답은 같은 항목에 먼저 쓴다:
         내 역할 → role, 최종 결과 → result, 근거와 숫자 → result에 "숫자로 보면: …" 한 줄로,
         산출물 요청 → artifact, 배운 점 → learned.
      5. 주요 기여(contribution)는 기록마다 "- MM/DD 한 일" 형식의 목록으로 쓴다 (예: "- 11/03 팀 API 연동을 맡았습니다.").
         문제와 해결(problem)도 "- " 목록으로 2 ~ 4줄.
      6. body에는 본문만 쓴다. 제목(# / ##), 기간·역할 줄은 쓰지 않는다(서버가 붙인다).
      7. 1인칭 "~했습니다" 문체. 팀이 한 일과 내가 한 일을 구분해서, 내가 한 일만 내 기여로 쓴다.

      [좋은 예]
      입력:
        [프로젝트] 사자록 · 멋사 회고 서비스 / 역할: PM, BACKEND
        [기록] (1) 2026-11-03 한 일: 팀 API 연동을 맡았다 / 고민·해결: 응답 형식을 프론트와 미리 맞췄다
               (2) 2026-11-05 한 일: 회고 화면 API를 만들었다
        [마치기 답] 근거와 숫자: 2주 동안 API 12개를 만들었다 / 배운 점: 명세를 먼저 맞추면 연동이 빨라진다
        [첨부] 없음
      출력:
      {"sections":[
        {"key":"role","body":null},
        {"key":"contribution","body":"- 11/03 팀 API 연동을 맡았습니다.\\n- 11/05 회고 화면 API를 만들었습니다."},
        {"key":"problem","body":"- 연동 전에 프론트와 응답 형식을 미리 맞췄습니다."},
        {"key":"result","body":"숫자로 보면: 2주 동안 API 12개를 만들었습니다."},
        {"key":"artifact","body":null},
        {"key":"learned","body":"명세를 먼저 맞추면 연동이 빨라진다는 것을 배웠습니다."}
      ]}

      [나쁜 예]
      {"key":"result","body":"서비스 사용자 200명을 모았습니다."}
      → 기록과 답에 "200명"이 없으면 지어낸 결과다. 근거가 없으면 null로 둔다.

      [애매할 때]
      - 기록마다 역할이 흩어져 있어 한 문장으로 정리하기 어려우면 role은 null로 둔다(마치기 답이 있으면 그 답을 쓴다).
      - 첨부가 있으면 artifact에 첨부 이름을 목록으로 쓰고, 첨부도 답도 없으면 null로 둔다.
      - 최종 결과는 없고 숫자만 있으면 result에 "숫자로 보면: …" 한 줄만 쓴다.

      [출력 형식]
      아래 JSON으로만 답한다. 다른 글은 쓰지 않는다. 6개 key를 모두, 위 순서대로 쓴다.
      {"sections":[{"key":"role","body":"..."},{"key":"contribution","body":"..."},{"key":"problem","body":null},{"key":"result","body":"..."},{"key":"artifact","body":null},{"key":"learned","body":"..."}]}
      """;

  /**
   * @param records 고른 기록 (만든 순서)
   * @param answered 답한(ANSWERED) 마치기 질문
   */
  public static String portfolioUser(
      Project project, List<ProjectRecord> records, List<QuestionView> answered) {
    StringBuilder sb = new StringBuilder();
    sb.append("[항목 목록]\n").append(String.join(" / ", PortfolioDraftResult.KEYS)).append("\n\n");
    appendProject(sb, project);
    appendRecords(sb, records);
    sb.append("\n[마치기 답]\n");
    if (answered.isEmpty()) {
      sb.append("없음\n");
    }
    answered.forEach(
        q -> {
          sb.append("- ").append(q.tag()).append('\n');
          line(sb, "질문", q.content());
          line(sb, "답", q.answer());
        });
    appendAttachments(sb);
    return sb.toString();
  }

  private static void appendProject(StringBuilder sb, Project project) {
    sb.append("[프로젝트]\n");
    line(sb, "이름", project.getName());
    line(sb, "부제", project.getSubtitle());
    if (project.getRoles() != null && !project.getRoles().isEmpty()) {
      line(sb, "역할", String.join(", ", project.getRoles().stream().map(Enum::name).toList()));
    }
  }

  private static void appendRecords(StringBuilder sb, List<ProjectRecord> records) {
    sb.append("\n[기록]\n");
    for (int i = 0; i < records.size(); i++) {
      ProjectRecord r = records.get(i);
      sb.append("(").append(i + 1).append(") ");
      sb.append(r.getCreatedAt() == null ? "" : r.getCreatedAt().toLocalDate()).append('\n');
      line(sb, "메모", r.getMemo());
      line(sb, "한 일", r.getDid());
      line(sb, "고민·해결", r.getConcern());
      line(sb, "선택과 이유", r.getDecision());
      line(sb, "결과·반응", r.getResult());
      line(sb, "다음", r.getNextStep());
    }
  }

  private static void appendAttachments(StringBuilder sb) {
    // TODO(첨부 담당): 기록·프로젝트 첨부의 종류(category)와 이름을 [첨부]로 붙인다 (attachmentService.findAll).
    sb.append("\n[첨부]\n없음\n");
  }

  private static void line(StringBuilder sb, String label, String value) {
    if (value != null && !value.isBlank()) {
      sb.append(label).append(": ").append(value).append('\n');
    }
  }
}
