package com.sajarok.domain.project.service;

import com.sajarok.domain.project.ai.FinishPrompts;
import com.sajarok.domain.project.ai.PortfolioDraftResult;
import com.sajarok.domain.project.ai.PortfolioDraftResult.Section;
import com.sajarok.domain.project.entity.Project;
import com.sajarok.domain.project.entity.ProjectRecord;
import com.sajarok.domain.question.dto.QuestionView;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * 33번 AI 포트폴리오 초안: 프롬프트 조립 → AI 호출 → 결과 검증을 한곳에서 한다. 트랜잭션 안에서 부르지 않는다(최대 50초).
 *
 * <p>AI 응답은 PortfolioDraftResult.validate를 통과해야 쓴다. 실패하면 AI_FAILED, 시간 초과는 AiClient가 AI_TIMEOUT으로
 * 던진다.
 */
@Component
public class PortfolioDrafter {

  /** AiClient 연결 전에 쓰는 결과: 6개 항목 모두 빈칸. */
  static final PortfolioDraftResult ALL_BLANK =
      new PortfolioDraftResult(
          PortfolioDraftResult.KEYS.stream().map(key -> new Section(key, null)).toList());

  /**
   * @param answered 답한(ANSWERED) 마치기 질문
   * @return key → 본문(없으면 null)
   */
  public Map<String, String> draft(
      Project project, List<ProjectRecord> records, List<QuestionView> answered) {
    String user = FinishPrompts.portfolioUser(project, records, answered);

    // TODO(AI 모듈 담당): AiClient가 생기면 아래 빈칸 결과 대신 호출한다.
    // PortfolioDraftResult raw =
    //     aiClient.call(FinishPrompts.PORTFOLIO_DRAFT_SYSTEM, user, PortfolioDraftResult.class);
    PortfolioDraftResult raw = ALL_BLANK;

    return raw.validate();
  }
}
