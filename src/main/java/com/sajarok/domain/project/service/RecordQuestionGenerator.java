package com.sajarok.domain.project.service;

import com.sajarok.domain.project.ai.RecordAttachment;
import com.sajarok.domain.project.ai.RecordPrompts;
import com.sajarok.domain.project.ai.RecordQuestionResult;
import com.sajarok.domain.project.ai.RecordQuestionResult.Question;
import com.sajarok.domain.project.ai.RecordQuestionResult.Validated;
import com.sajarok.domain.project.entity.ProjectRecord;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.stereotype.Component;

/** 30번 AI 기록 질문: 프롬프트 조립 → AI 호출 → 검증. 트랜잭션 안에서 부르지 않는다(최대 50초). */
@Component
public class RecordQuestionGenerator {

  /** AiClient 연결 전에 쓰는 고정 질문 (H-13 프로토타입 문구). 근거 문장 없음. */
  static final RecordQuestionResult DEFAULT_QUESTIONS =
      new RecordQuestionResult(
          false,
          List.of(
              new Question("내 역할·과정", null, null, "이 기록에서 내가 맡은 일은 뭐였어요?"),
              new Question("내 역할·과정", null, null, "결론은 어떤 과정을 거쳐 나왔어요? 의견이 갈린 부분이 있었나요?"),
              new Question("선택의 이유", null, null, "가장 고민한 결정은 뭐였고, 왜 그렇게 정했어요?"),
              new Question("근거와 숫자", null, null, "결과나 팀·사용자 반응이 있었나요? 확인한 것만요.")));

  public Validated generate(ProjectRecord record, List<RecordAttachment> attachments) {
    String user = RecordPrompts.questionsUser(record, attachments);

    // TODO(AI 모듈 담당): AiClient가 생기면 아래 고정 질문 대신 호출한다.
    // RecordQuestionResult raw =
    //     aiClient.call(RecordPrompts.QUESTIONS_SYSTEM, user, RecordQuestionResult.class);
    RecordQuestionResult raw = DEFAULT_QUESTIONS;

    Map<Long, String> attachmentTexts =
        attachments.stream()
            .collect(Collectors.toMap(RecordAttachment::id, RecordAttachment::text, (a, b) -> a));
    return raw.validate(recordText(record), attachmentTexts);
  }

  private static String recordText(ProjectRecord record) {
    return Stream.of(
            record.getMemo(),
            record.getDid(),
            record.getConcern(),
            record.getDecision(),
            record.getResult(),
            record.getNextStep())
        .filter(v -> v != null && !v.isBlank())
        .collect(Collectors.joining("\n"));
  }
}
