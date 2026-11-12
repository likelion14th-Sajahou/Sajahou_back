package com.sajarok.domain.project.service;

import com.sajarok.domain.project.ai.FinishAttachments;
import com.sajarok.domain.project.ai.FinishPrompts;
import com.sajarok.domain.project.ai.FinishQuestionResult;
import com.sajarok.domain.project.entity.Project;
import com.sajarok.domain.project.entity.ProjectRecord;
import com.sajarok.domain.question.dto.NewQuestion;
import com.sajarok.infra.ai.AiClient;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** 32번 AI 마치기 질문: 프롬프트 조립 → AI 호출 → 검증. 트랜잭션 안에서 부르지 않는다(최대 50초). */
@Component
@RequiredArgsConstructor
public class FinishQuestionGenerator {

  private final AiClient aiClient;

  public List<NewQuestion> generate(
      Project project, List<ProjectRecord> records, FinishAttachments attachments) {
    String user = FinishPrompts.questionsUser(project, records, attachments);
    return aiClient
        .call(FinishPrompts.QUESTIONS_SYSTEM, user, FinishQuestionResult.class)
        .validate();
  }
}
