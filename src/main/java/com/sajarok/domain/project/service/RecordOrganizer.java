package com.sajarok.domain.project.service;

import com.sajarok.domain.project.ai.RecordOrganizeResult;
import com.sajarok.domain.project.ai.RecordPrompts;
import com.sajarok.domain.project.entity.ProjectRecord;
import com.sajarok.domain.question.dto.QuestionView;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/** 31번 AI 기록 정리: 프롬프트 조립 → AI 호출 → 검증. 트랜잭션 안에서 부르지 않는다(최대 50초). */
@Component
public class RecordOrganizer {

  /**
   * @return key → 제안(없으면 null)
   */
  public Map<String, String> organize(
      ProjectRecord record, List<QuestionView> answered, List<String> attachmentTexts) {
    String user = RecordPrompts.organizeUser(record, answered, attachmentTexts);

    // TODO(AI 모듈 담당): AiClient가 생기면 아래 빈 결과 대신 호출한다.
    // RecordOrganizeResult raw =
    //     aiClient.call(RecordPrompts.ORGANIZE_SYSTEM, user, RecordOrganizeResult.class);
    RecordOrganizeResult raw = new RecordOrganizeResult(null, null, null, null, null);

    return raw.validate();
  }
}
