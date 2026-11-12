package com.sajarok.domain.retrospective.service;

import com.sajarok.domain.question.dto.NewQuestion;
import com.sajarok.domain.question.dto.QuestionView;
import com.sajarok.domain.question.entity.QuestionOwner;
import com.sajarok.domain.question.service.AiQuestionService;
import com.sajarok.domain.retrospective.entity.Retrospective;
import com.sajarok.domain.retrospective.repository.RetrospectiveRepository;
import com.sajarok.global.exception.BusinessException;
import com.sajarok.global.exception.ErrorCode;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class RetroQuestionWriter {
  private final RetrospectiveRepository retrospectiveRepository;
  private final AiQuestionService aiQuestionService;

  /** 질문 교체 + 해시 저장을 한 트랜잭션으로. AI를 기다리는 사이 회고가 지워졌으면 NOT_FOUND */
  @Transactional
  public List<QuestionView> replace(
      Long retrospectiveId, List<NewQuestion> questions, String hash) {
    findRetrospective(retrospectiveId).recordQuestionInput(hash);
    return aiQuestionService.replace(QuestionOwner.retrospective(retrospectiveId), questions);
  }

  /** CONTINUE: 질문은 그대로 두고 해시만 바꾼다 */
  @Transactional
  public void continueWith(Long retrospectiveId, String hash) {
    findRetrospective(retrospectiveId).recordQuestionInput(hash);
  }

  private Retrospective findRetrospective(Long retrospectiveId) {
    return retrospectiveRepository
        .findById(retrospectiveId)
        .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
  }
}
