package com.sajarok.domain.project.service;

import com.sajarok.domain.project.ai.FinishAttachments;
import com.sajarok.domain.project.ai.FinishPrompts;
import com.sajarok.domain.project.ai.PortfolioDraftResult;
import com.sajarok.domain.project.entity.Project;
import com.sajarok.domain.project.entity.ProjectRecord;
import com.sajarok.domain.question.dto.QuestionView;
import com.sajarok.infra.ai.AiClient;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** 33번 AI 포트폴리오 초안: 프롬프트 조립 → AI 호출 → 검증. 트랜잭션 안에서 부르지 않는다(최대 50초). */
@Component
@RequiredArgsConstructor
public class PortfolioDrafter {

  private final AiClient aiClient;

  /**
   * @param answered 답한(ANSWERED) 마치기 질문
   * @return key → 본문(없으면 null)
   */
  public Map<String, String> draft(
      Project project,
      List<ProjectRecord> records,
      List<QuestionView> answered,
      FinishAttachments attachments) {
    String user = FinishPrompts.portfolioUser(project, records, answered, attachments);
    return aiClient
        .call(FinishPrompts.PORTFOLIO_DRAFT_SYSTEM, user, PortfolioDraftResult.class)
        .validate();
  }
}
