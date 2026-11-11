package com.sajarok.domain.project.ai;

import com.sajarok.domain.project.entity.ProjectRecord;
import com.sajarok.domain.question.dto.QuestionView;
import java.util.List;

/** 프로젝트 기록 AI(30·31번) 프롬프트. 호출은 RecordQuestionGenerator·RecordOrganizer에서 트랜잭션 밖에서 한다. */
public final class RecordPrompts {

  private RecordPrompts() {}

  /**
   * 30번 system 프롬프트 초안. 태그·category 기준은 H-13, 좋은 예 질문은 프로토타입 문구. TODO(AI 모듈 연결 후): 실제 응답 보며 다듬기.
   */
  public static final String QUESTIONS_SYSTEM =
      """
      너는 아기사자가 남긴 프로젝트 기록 메모와 첨부를 읽고, 기록을 더 채울 수 있게 질문하는 도우미다.

      [규칙]
      1. 질문은 1 ~ %d개. tag는 [태그 목록]의 이름을 그대로 쓴다: %s. 같은 태그를 여러 번 써도 된다.
      2. 첨부 종류에 따라 묻는다:
         - 회의록(MEETING_NOTE)·발표 자료(PRESENTATION)·산출물(OUTPUT) → 내 역할·과정
         - 디자인(DESIGN)·링크(LINK) → 선택의 이유
         - 기타(OTHER) → 종류로 정하지 말고 내용을 읽고 맞는 태그를 고른다
         - 근거와 숫자 질문은 첨부와 상관없이 항상 하나 넣는다.
      3. quote에는 질문의 근거가 된 문장을 [메모·기록] 또는 [첨부] 원문에서 한 글자도 바꾸지 않고 옮긴다.
         첨부에서 옮겼으면 sourceAttachmentId에 그 첨부 번호를, 메모·기록에서 옮겼으면 null을 쓴다. 근거 문장이 없으면 quote도 null.
      4. 기록에 없는 사실을 질문 안에 넣지 않는다(질문으로 사실을 지어내지 않는다).
      5. 이미 [메모·기록]에 답이 있는 것은 묻지 않는다. 확인할 수 있는 것만 묻는다.
      6. 한국어 존댓말, 한 질문에 한 가지만 묻는다(짧은 두 문장까지).

      [좋은 예]
      입력:
        [메모·기록] 메모: 11/3 기획 회의. 홈 화면 구성 논의. 나는 시안 2개 준비
        [첨부] (601) FILE / MEETING_NOTE / 회의록.pdf: 홈 화면에 무엇을 먼저 보여 줄지 논의함. 회고 마감 강조안과 프로젝트 함께 보기안 비교
               (602) FILE / DESIGN / 홈 시안.png: 시안 A 회고 마감 강조, 시안 B 프로젝트 함께 보기
      출력:
      {"insufficientMaterial":false,"questions":[
        {"tag":"내 역할·과정","quote":"홈 화면에 무엇을 먼저 보여 줄지 논의함","sourceAttachmentId":601,
         "content":"회의록을 보니 홈 화면에 무엇을 먼저 보여 줄지 논의했네요. 이 회의에서 내가 맡은 일은 뭐였어요?"},
        {"tag":"내 역할·과정","quote":"회고 마감 강조안과 프로젝트 함께 보기안 비교","sourceAttachmentId":601,
         "content":"결론은 어떤 과정을 거쳐 나왔어요? 의견이 갈린 부분이 있었나요?"},
        {"tag":"선택의 이유","quote":"시안 A 회고 마감 강조, 시안 B 프로젝트 함께 보기","sourceAttachmentId":602,
         "content":"첨부한 시안에서 가장 고민한 결정은 뭐였고, 왜 그렇게 정했어요?"},
        {"tag":"근거와 숫자","quote":null,"sourceAttachmentId":null,
         "content":"결과나 팀·사용자 반응이 있었나요? 확인한 것만요."}
      ]}

      [나쁜 예]
      {"tag":"근거와 숫자","quote":"시안 B 클릭률 30%% 상승","sourceAttachmentId":602,"content":"클릭률이 30%% 올랐다고 했는데 어떻게 측정했어요?"}
      → 첨부에 "클릭률"이나 "30%%"가 없다. quote를 지어내거나 질문에 없는 사실을 넣으면 안 된다.

      [애매할 때]
      - 메모가 "회의함"처럼 너무 짧고 첨부도 없어 물을 거리가 없으면 질문하지 않는다: {"insufficientMaterial":true,"questions":[]}
      - 근거 문장을 원문에서 그대로 찾을 수 없으면 quote와 sourceAttachmentId를 null로 둔다(문장을 고쳐 쓰지 않는다).

      [출력 형식]
      아래 JSON으로만 답한다. 다른 글은 쓰지 않는다.
      {"insufficientMaterial":false,"questions":[{"tag":"내 역할·과정","quote":"...","sourceAttachmentId":601,"content":"..."}]}
      """
          .formatted(
              RecordQuestionResult.MAX_QUESTIONS, String.join(" / ", RecordQuestionResult.TAGS));

  /**
   * @param attachments 추출이 끝난(DONE) 첨부
   */
  public static String questionsUser(ProjectRecord record, List<RecordAttachment> attachments) {
    StringBuilder sb = new StringBuilder();
    sb.append("[태그 목록]\n").append(String.join(" / ", RecordQuestionResult.TAGS)).append("\n\n");
    sb.append("[메모·기록]\n");
    int before = sb.length();
    line(sb, "메모", record.getMemo());
    line(sb, "한 일", record.getDid());
    line(sb, "고민·해결", record.getConcern());
    line(sb, "선택과 이유", record.getDecision());
    line(sb, "결과·반응", record.getResult());
    line(sb, "다음", record.getNextStep());
    if (sb.length() == before) {
      sb.append("없음\n");
    }
    sb.append("\n[첨부]\n");
    if (attachments.isEmpty()) {
      sb.append("없음\n");
    }
    attachments.forEach(
        a ->
            sb.append("(")
                .append(a.id())
                .append(") ")
                .append(a.type())
                .append(" / ")
                .append(a.category())
                .append(" / ")
                .append(a.name())
                .append(": ")
                .append(a.text())
                .append('\n'));
    return sb.toString();
  }

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
