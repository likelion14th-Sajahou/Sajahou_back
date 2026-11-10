package com.sajarok.domain.project.service;

import com.sajarok.domain.project.dto.FinishQuestionsResponse;
import com.sajarok.domain.project.dto.PortfolioSaveResponse;
import com.sajarok.domain.project.dto.ProjectDetailResponse.Portfolio;
import com.sajarok.domain.project.entity.Project;
import com.sajarok.domain.project.entity.ProjectRecord;
import com.sajarok.domain.project.entity.ProjectRecordStatus;
import com.sajarok.domain.project.repository.ProjectRecordRepository;
import com.sajarok.domain.project.repository.ProjectRepository;
import com.sajarok.domain.question.dto.NewQuestion;
import com.sajarok.domain.question.dto.QuestionView;
import com.sajarok.domain.question.entity.QuestionOwner;
import com.sajarok.domain.question.service.AiQuestionService;
import com.sajarok.global.exception.BusinessException;
import com.sajarok.global.exception.ErrorCode;
import com.sajarok.global.time.KstTime;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 프로젝트 마치기 (32·33·34번). */
@Service
@RequiredArgsConstructor
public class ProjectFinishService {

  private final ProjectRepository projectRepository;
  private final AiQuestionService aiQuestionService;
  private final ProjectRecordRepository projectRecordRepository;
  private final FinishQuestionGenerator finishQuestionGenerator;

  /** 32번 결과. created가 true면 새로 만듦(201), false면 진행 중이던 질문을 그대로 돌려줌(200). */
  public record FinishQuestions(boolean created, FinishQuestionsResponse response) {}

  /**
   * 32번: 마치기 질문. 이 프로젝트의 마치기 질문이 이미 있으면(34번 저장 전) AI를 부르지 않고 질문과 답을 그대로 돌려준다.
   *
   * <p>없으면 고른 기록(중복은 합침)으로 포트폴리오에 빠진 항목을 묻는 질문을 만든다. AI는 트랜잭션 밖에서 부르고(최대 50초), 저장은
   * AiQuestionService.replace의 트랜잭션에서 한다. AI가 실패하면 아무것도 저장하지 않는다.
   *
   * <p>남의 프로젝트거나 없으면 NOT_FOUND. 고른 기록 중 이 프로젝트의 SAVED 기록이 아닌 것이 있어도 NOT_FOUND.
   */
  public FinishQuestions createQuestions(Long userId, Long projectId, List<Long> recordIds) {
    // TODO(AI 모듈 담당): AiLock이 생기면 프로젝트 단위로 감싼다. 진행 중이면 AI_IN_PROGRESS.
    Project project =
        projectRepository
            .findByIdAndUserId(projectId, userId)
            .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
    QuestionOwner owner = QuestionOwner.project(projectId);

    List<QuestionView> existing = aiQuestionService.findAll(owner);
    if (!existing.isEmpty()) {
      return new FinishQuestions(false, FinishQuestionsResponse.from(existing));
    }

    Set<Long> ids = new LinkedHashSet<>(recordIds);
    List<ProjectRecord> records =
        projectRecordRepository.findAllByProjectIdAndStatusAndIdInOrderByCreatedAtAscIdAsc(
            projectId, ProjectRecordStatus.SAVED, ids);
    if (records.size() != ids.size()) {
      throw new BusinessException(ErrorCode.NOT_FOUND);
    }

    List<NewQuestion> questions = finishQuestionGenerator.generate(project, records);
    return new FinishQuestions(
        true, FinishQuestionsResponse.from(aiQuestionService.replace(owner, questions)));
  }

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
