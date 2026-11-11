package com.sajarok.domain.project.service;

import com.sajarok.domain.project.ai.RecordOrganizeResult;
import com.sajarok.domain.project.ai.RecordPrompts;
import com.sajarok.domain.project.entity.ProjectRecord;
import com.sajarok.domain.question.dto.QuestionView;
import com.sajarok.infra.ai.AiClient;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** 31번 AI 기록 정리: 프롬프트 조립 → AI 호출 → 검증. 트랜잭션 안에서 부르지 않는다(최대 50초). */
@Component
@RequiredArgsConstructor
public class RecordOrganizer {

  private final AiClient aiClient;

  /**
   * @return key → 제안(없으면 null)
   */
  public Map<String, String> organize(
      ProjectRecord record, List<QuestionView> answered, List<String> attachmentTexts) {
    String user = RecordPrompts.organizeUser(record, answered, attachmentTexts);
    return aiClient
        .call(RecordPrompts.ORGANIZE_SYSTEM, user, RecordOrganizeResult.class)
        .validate();
  }
}
