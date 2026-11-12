package com.sajarok.domain.project.service;

import com.sajarok.domain.project.ai.RecordAttachment;
import com.sajarok.domain.project.ai.RecordPrompts;
import com.sajarok.domain.project.ai.RecordQuestionResult;
import com.sajarok.domain.project.ai.RecordQuestionResult.Validated;
import com.sajarok.domain.project.entity.ProjectRecord;
import com.sajarok.infra.ai.AiClient;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** 30번 AI 기록 질문: 프롬프트 조립 → AI 호출 → 검증. 트랜잭션 안에서 부르지 않는다(최대 50초). */
@Component
@RequiredArgsConstructor
public class RecordQuestionGenerator {

  private final AiClient aiClient;

  public Validated generate(ProjectRecord record, List<RecordAttachment> attachments) {
    String user = RecordPrompts.questionsUser(record, attachments);
    RecordQuestionResult raw =
        aiClient.call(RecordPrompts.QUESTIONS_SYSTEM, user, RecordQuestionResult.class);

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
