package com.sajarok.domain.project.service;

import com.sajarok.domain.project.dto.PortfolioSaveResponse;
import com.sajarok.domain.project.dto.ProjectDetailResponse.Portfolio;
import com.sajarok.domain.project.entity.Project;
import com.sajarok.domain.project.repository.ProjectRepository;
import com.sajarok.domain.question.entity.QuestionOwner;
import com.sajarok.domain.question.service.AiQuestionService;
import com.sajarok.global.exception.BusinessException;
import com.sajarok.global.exception.ErrorCode;
import com.sajarok.global.time.KstTime;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 프로젝트 마치기 (32·33·34번). 32·33번(AI)은 AiClient가 생기면 여기에 추가한다. */
@Service
@RequiredArgsConstructor
public class ProjectFinishService {

  private final ProjectRepository projectRepository;
  private final AiQuestionService aiQuestionService;

  /**
   * 34번: 포트폴리오 저장. 처음 저장이면 완료(DONE)로 바꾸고, 다시 저장이면 포트폴리오만 덮어쓴다. 이 프로젝트의 마치기 질문은 지운다.
   *
   * <p>남의 프로젝트거나 없으면 NOT_FOUND. 저장 시각은 JVM 시간대와 관계없이 한국 시간으로 찍는다(JVM 시간대는 팀과 정하는 중).
   */
  @Transactional
  public PortfolioSaveResponse savePortfolio(Long userId, Long projectId, String markdown) {
    Project project =
        projectRepository
            .findByIdAndUserId(projectId, userId)
            .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));

    boolean firstCompletion =
        project.completeWithPortfolio(markdown, LocalDateTime.now(KstTime.OFFSET));
    aiQuestionService.deleteAll(QuestionOwner.project(projectId));

    return new PortfolioSaveResponse(
        project.getStatus(),
        firstCompletion,
        new Portfolio(
            project.getPortfolioMarkdown(), KstTime.toOffset(project.getPortfolioSavedAt())));
  }
}
