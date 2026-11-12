package com.sajarok.domain.project.ai;

import com.sajarok.domain.project.entity.ProjectRecord;
import com.sajarok.domain.question.dto.QuestionView;
import java.util.ArrayList;
import java.util.List;

/** 프로젝트 기록 AI(30·31번) 프롬프트. 호출은 RecordQuestionGenerator·RecordOrganizer에서 트랜잭션 밖에서 한다. */
public final class RecordPrompts {

  private RecordPrompts() {}

  /**
   * 30번 system 프롬프트. 빈칸은 서버가 계산해 [빈칸]으로 넘기고, 빈칸 하나에 질문 하나를 만든다(태그는 H-13의 3개 그대로). 좋은 예는 ai-eval 공통
   * 자료(프로토타입 홈 시안 회의).
   */
  public static final String QUESTIONS_SYSTEM =
      """
      너는 아기사자가 남긴 프로젝트 기록과 첨부를 읽고, 기록의 빈칸을 채울 수 있게 질문하는 도우미다.

      [규칙]
      1. 먼저 물을 거리가 있는지 본다. [메모·기록]이 "회의함"처럼 무엇을 했는지 알 수 없을 만큼 짧고 [첨부]도 없으면,
         [빈칸]과 상관없이 질문하지 않는다: {"insufficientMaterial":true,"questions":[]}
      2. [빈칸]에 적힌 칸마다 질문을 하나씩 만든다. 질문은 최대 %d개이고, [빈칸]의 순서대로 쓴다.
         빈칸이 %d개보다 많으면 결과·반응 → 선택과 이유 → 고민·해결 → 한 일 → 다음 순서로 먼저 고른다.
      3. tag는 [태그 목록]의 이름을 그대로 쓴다: %s. 같은 태그를 여러 질문에 써도 된다.
         칸에 맞춰 고른다:
         - 한 일·고민·해결·다음 → 내 역할·과정
         - 선택과 이유 → 선택의 이유
         - 결과·반응 → 근거와 숫자
      4. [메모·기록]이나 [첨부]에 그 칸과 관련된 문장이 있으면 그 문장을 짚고 묻는다.
         quote에 한 글자도 바꾸지 않고 옮긴다. 첨부에서 옮겼으면 sourceAttachmentId에 그 첨부 번호를, 메모·기록이면 null을 쓴다.
         짚을 문장이 없으면 quote와 sourceAttachmentId를 null로 두고, 그 칸에 무엇이 비었는지 묻는다.
      5. 질문 하나는 그 칸 하나만 묻는다. 이미 적힌 내용은 다시 묻지 않는다.
         빈칸의 내용이 다른 칸이나 메모에 이미 적혀 있으면(예: 한 일에 결과가 같이 적힘) 그 내용 말고 더 필요한 것을 묻는다.
      6. 자료에 없는 사실을 질문에 전제하지 않는다. "성공적이었는데", "30%% 올랐는데"처럼 자료에 없는 결과를 깔고 묻지 않는다.
      7. 첨부는 내용을 보고 관련된 칸의 질문에 쓴다. 회의록·발표 자료·산출물은 주로 한 일·고민·해결, 디자인·링크는 주로 선택과 이유에 쓴다.
         기타(OTHER)는 종류로 정하지 말고 내용을 읽고 맞는 칸에 쓴다.
      8. 한국어 존댓말, 짧은 두 문장까지.

      [좋은 예 1] 빈칸 4개 → 질문 4개
      입력:
        [메모·기록] 한 일: 기획 회의에서 홈 화면 시안 2개를 준비했다
        [첨부] (602) FILE / DESIGN / 홈 시안.png: 시안 A 회고 마감 강조, 시안 B 프로젝트 함께 보기
        [빈칸] 고민·해결 / 선택과 이유 / 결과·반응 / 다음
      출력:
      {"insufficientMaterial":false,"questions":[
        {"tag":"내 역할·과정","quote":"기획 회의에서 홈 화면 시안 2개를 준비했다","sourceAttachmentId":null,
         "content":"시안을 준비하면서 팀과 의견이 갈리거나 막힌 부분이 있었나요? 어떻게 풀었는지도 알려 주세요."},
        {"tag":"선택의 이유","quote":"시안 A 회고 마감 강조, 시안 B 프로젝트 함께 보기","sourceAttachmentId":602,
         "content":"두 시안 중 어느 쪽으로 정했고, 왜 그렇게 정했어요?"},
        {"tag":"근거와 숫자","quote":null,"sourceAttachmentId":null,
         "content":"결과나 팀·사용자 반응이 있었나요? 확인한 것만요."},
        {"tag":"내 역할·과정","quote":null,"sourceAttachmentId":null,
         "content":"이 회의 다음에 이어서 하기로 한 일이 있나요?"}
      ]}

      [좋은 예 2] 빈칸 3개 → 질문 3개
      입력:
        [메모·기록] 한 일: 기획 회의에서 홈 화면 시안 2개를 준비했다
                    고민·해결: 개발자는 회고 하나만 크게 보여 주자고 했고, 나는 프로젝트도 같이 보여야 한다고 봤다
        [첨부] 없음
        [빈칸] 선택과 이유 / 결과·반응 / 다음
      출력:
      {"insufficientMaterial":false,"questions":[
        {"tag":"선택의 이유","quote":"나는 프로젝트도 같이 보여야 한다고 봤다","sourceAttachmentId":null,
         "content":"의견이 갈렸을 때 최종으로 어떻게 정했고, 왜 그렇게 정했어요?"},
        {"tag":"근거와 숫자","quote":null,"sourceAttachmentId":null,
         "content":"그렇게 정한 뒤 확인한 반응이나 숫자가 있었나요? 확인한 것만요."},
        {"tag":"내 역할·과정","quote":null,"sourceAttachmentId":null,
         "content":"다음에 이어서 하기로 한 일이 있나요?"}
      ]}
      → 고민·해결은 이미 채워져 있어 묻지 않는다.

      [나쁜 예]
      {"tag":"근거와 숫자","quote":"시안 B 클릭률 30%% 상승","sourceAttachmentId":602,"content":"클릭률이 30%% 올랐다고 했는데 어떻게 측정했어요?"}
      → 첨부에 "클릭률"이나 "30%%"가 없다. quote를 지어내거나 질문에 없는 사실을 넣으면 안 된다.
      {"tag":"근거와 숫자","quote":null,"sourceAttachmentId":null,"content":"시안 2개는 각각 어떤 차이였나요?"}
      → 태그와 질문이 맞지 않는다. 시안의 차이는 한 일·과정을 묻는 것이다.
      {"tag":"내 역할·과정","quote":null,"sourceAttachmentId":null,"content":"무엇을 배웠나요?"}
      → 어떤 칸도 채우지 않는 뻔한 질문이다.

      [애매할 때]
      - 메모가 "회의함"처럼 너무 짧고 첨부도 없어 물을 거리가 없으면 질문하지 않는다: {"insufficientMaterial":true,"questions":[]}
      - 근거 문장을 원문에서 그대로 찾을 수 없으면 quote와 sourceAttachmentId를 null로 둔다(문장을 고쳐 쓰지 않는다).
      - [빈칸]이 "없음"이면 근거와 숫자 질문 하나만 한다(채운 내용을 뒷받침할 숫자·반응이 있는지).

      [출력 형식]
      아래 JSON으로만 답한다. 다른 글은 쓰지 않는다.
      - insufficientMaterial: 물을 거리가 없으면 true(이때 questions는 빈 배열)
      - tag: [태그 목록] 중 하나
      - quote: 질문이 짚은 원문 문장 그대로. 없으면 null
      - sourceAttachmentId: quote가 나온 첨부 번호. 메모·기록에서 나왔거나 quote가 null이면 null
      - content: 아기사자에게 보여 줄 질문
      {"insufficientMaterial":false,"questions":[{"tag":"내 역할·과정","quote":"...","sourceAttachmentId":601,"content":"..."}]}
      """
          .formatted(
              RecordQuestionResult.MAX_QUESTIONS,
              RecordQuestionResult.MAX_QUESTIONS,
              String.join(" / ", RecordQuestionResult.TAGS));

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
    sb.append("\n[빈칸]\n").append(blanks(record)).append('\n');
    return sb.toString();
  }

  /** 5칸 중 빈 칸 이름. AI가 빈칸을 직접 찾게 하면 질문 수가 흔들려서 서버가 계산해 넘긴다. 메모는 칸이 아니라서 넣지 않는다. */
  static String blanks(ProjectRecord record) {
    List<String> empty = new ArrayList<>();
    addIfBlank(empty, "한 일", record.getDid());
    addIfBlank(empty, "고민·해결", record.getConcern());
    addIfBlank(empty, "선택과 이유", record.getDecision());
    addIfBlank(empty, "결과·반응", record.getResult());
    addIfBlank(empty, "다음", record.getNextStep());
    return empty.isEmpty() ? "없음" : String.join(" / ", empty);
  }

  private static void addIfBlank(List<String> names, String name, String value) {
    if (value == null || value.isBlank()) {
      names.add(name);
    }
  }

  /** 31번 system 프롬프트 초안. 좋은 예는 H-14 프로토타입 문구. TODO(AI 모듈 연결 후): 실제 응답을 보면서 다듬는다. */
  public static final String ORGANIZE_SYSTEM =
      """
      너는 아기사자의 프로젝트 기록 메모와 자료를 5칸으로 정리해 주는 도우미다.

      [규칙]
      1. 자료에 없는 숫자·성과는 쓰지 않는다. 숫자·결과·반응은 [메모]·[이미 쓴 칸]·[첨부]·[AI 질문 답]에 있는 것만 쓴다.
      2. 자료에 없는 기능·사실도 지어내지 않는다.
      3. 칸은 did(한 일) / concern(고민·해결) / decision(선택과 이유) / result(결과·반응) / nextStep(다음) 5개다.
      4. [이미 쓴 칸]에 내용이 있는 칸은 null로 둔다(사용자 글을 그대로 쓴다). 빈 칸만 채운다.
      5. 근거가 없는 칸은 null로 둔다. 비슷한 내용으로 채우지 않는다.
      6. 각 칸은 1 ~ 3문장, "~했다" 기록 문체. 내가 한 일 중심으로 쓴다.
      7. 첨부 내용은 이 기록과 관련된 부분만 쓴다.

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
      아래 JSON으로만 답한다. 다른 글은 쓰지 않는다. 5개 칸을 모두 쓰고, 근거가 없거나 이미 쓴 칸은 null.
      - did: 한 일. 내가 직접 한 일
      - concern: 고민·해결. 부딪힌 문제와 어떻게 풀었는지
      - decision: 선택과 이유. 무엇을 골랐고 왜 그랬는지
      - result: 결과·반응. 자료에 있는 결과·숫자·반응만
      - nextStep: 다음. 자료에 적힌 앞으로 할 일
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
