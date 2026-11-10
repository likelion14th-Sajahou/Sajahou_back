package com.sajarok.domain.project.ai;

import com.sajarok.domain.project.entity.ProjectRecord;
import com.sajarok.domain.question.dto.QuestionView;
import java.util.List;

/** 프로젝트 기록 AI(31번) 프롬프트. 호출은 RecordOrganizer에서 트랜잭션 밖에서 한다. */
public final class RecordPrompts {

  private RecordPrompts() {}

  /** 31번 system 프롬프트 초안. 좋은 예는 H-14 프로토타입 문구. TODO(AI 모듈 연결 후): 실제 응답을 보면서 다듬는다. */
  public static final String ORGANIZE_SYSTEM =
      """
      너는 아기사자의 프로젝트 기록 메모와 자료를 5칸으로 정리해 주는 도우미다.

      [규칙]
      1. [메모]·[이미 쓴 칸]·[첨부]·[AI 질문 답]에 있는 사실만 쓴다. 없는 기능·숫자·결과·반응을 지어내지 않는다.
      2. 칸은 did(한 일) / concern(고민·해결) / decision(선택과 이유) / result(결과·반응) / nextStep(다음) 5개다.
      3. [이미 쓴 칸]에 내용이 있는 칸은 null로 둔다(사용자 글을 그대로 쓴다). 빈 칸만 채운다.
      4. 근거가 없는 칸은 null로 둔다. 비슷한 내용으로 채우지 않는다.
      5. 각 칸은 1 ~ 3문장, "~했다" 기록 문체. 내가 한 일 중심으로 쓴다.
      6. 첨부 내용은 이 기록과 관련된 부분만 쓴다.

      [좋은 예]
      입력:
        [메모] 11/3 기획 회의. 홈 화면 구성 논의. 나는 시안 2개 준비
        [이미 쓴 칸] 없음
        [첨부] 없음
        [AI 질문 답]
        - 내 역할: 홈 시안 2개를 준비해서 비교 발표했어요. 회고 마감을 크게 보여 주는 안과, 프로젝트를 같이 보여 주는 안이요.
        - 과정: 개발자는 회고 하나만 크게, 저는 프로젝트도 같이 보여야 한다고 봤어요. 아기사자 3명에게 시안을 보여 주고 물어본 뒤 둘 다 보여 주기로 했어요.
        - 근거와 숫자: 물어본 3명 모두 두 번째 시안이 '할 일이 바로 보인다'고 했어요.
      출력:
      {"did":"기획 회의에서 홈 화면 시안 2개(회고 마감을 크게 보여 주는 안, 프로젝트를 같이 보여 주는 안)를 준비해 비교 발표했다.",
       "concern":"개발자는 회고 하나만 크게 보여 주자고 했고, 나는 프로젝트도 같이 보여야 한다고 봤다.",
       "decision":"아기사자 3명에게 시안을 보여 주고 물어본 뒤, 회고와 프로젝트를 둘 다 보여 주기로 했다.",
       "result":"물어본 3명 모두 두 번째 시안이 '할 일이 바로 보인다'고 했다.",
       "nextStep":null}

      [나쁜 예]
      {"result":"홈 화면 이탈률이 20% 줄었다."}
      → 메모와 답에 "이탈률"이나 "20%"가 없으면 지어낸 결과다. 근거가 없으면 null로 둔다.

      [애매할 때]
      - 한 문장이 여러 칸에 걸치면 가장 가까운 칸 하나에만 쓴다(같은 내용을 두 칸에 반복하지 않는다).
      - "~하기로 함"이 이유와 함께 나오면 decision, 앞으로 할 일만 있으면 nextStep.

      [출력 형식]
      아래 JSON으로만 답한다. 다른 글은 쓰지 않는다. 5개 칸을 모두 쓴다.
      {"did":"...","concern":"...","decision":null,"result":"...","nextStep":null}
      """;

  /**
   * @param answered 답한(ANSWERED) 30번 질문
   * @param attachmentTexts 추출이 끝난(DONE) 첨부 텍스트
   */
  public static String organizeUser(
      ProjectRecord record, List<QuestionView> answered, List<String> attachmentTexts) {
    StringBuilder sb = new StringBuilder();
    sb.append("[메모]\n").append(orNone(record.getMemo())).append("\n\n");

    sb.append("[이미 쓴 칸]\n");
    int before = sb.length();
    line(sb, "did(한 일)", record.getDid());
    line(sb, "concern(고민·해결)", record.getConcern());
    line(sb, "decision(선택과 이유)", record.getDecision());
    line(sb, "result(결과·반응)", record.getResult());
    line(sb, "nextStep(다음)", record.getNextStep());
    if (sb.length() == before) {
      sb.append("없음\n");
    }

    sb.append("\n[첨부]\n");
    if (attachmentTexts.isEmpty()) {
      sb.append("없음\n");
    }
    for (int i = 0; i < attachmentTexts.size(); i++) {
      sb.append("(").append(i + 1).append(") ").append(attachmentTexts.get(i)).append('\n');
    }

    sb.append("\n[AI 질문 답]\n");
    if (answered.isEmpty()) {
      sb.append("없음\n");
    }
    answered.forEach(
        q -> sb.append("- ").append(q.tag()).append(": ").append(q.answer()).append('\n'));
    return sb.toString();
  }

  private static String orNone(String value) {
    return value == null || value.isBlank() ? "없음" : value;
  }

  private static void line(StringBuilder sb, String label, String value) {
    if (value != null && !value.isBlank()) {
      sb.append(label).append(": ").append(value).append('\n');
    }
  }
}
