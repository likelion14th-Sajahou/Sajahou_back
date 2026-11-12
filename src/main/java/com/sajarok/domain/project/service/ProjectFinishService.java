package com.sajarok.domain.project.service;

import com.sajarok.domain.attachment.dto.AttachmentOwner;
import com.sajarok.domain.attachment.service.AttachmentService;
import com.sajarok.domain.project.ai.FinishAttachments;
import com.sajarok.domain.project.dto.FinishQuestionsResponse;
import com.sajarok.domain.project.dto.PortfolioDraftResponse;
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
import com.sajarok.domain.question.entity.QuestionStatus;
import com.sajarok.domain.question.service.AiQuestionService;
import com.sajarok.global.exception.BusinessException;
import com.sajarok.global.exception.ErrorCode;
import com.sajarok.global.time.KstTime;
import com.sajarok.infra.ai.AiLock;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
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
  private final PortfolioDrafter portfolioDrafter;
  private final AttachmentService attachmentService;
  private final AiLock aiLock;

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
    // 잠금 전에 소유부터 확인해 남의 ID로는 잠금을 잡지 않는다. 32·33번이 같은 키.
    checkProjectOwner(userId, projectId);
    return aiLock.run(
        finishLockKey(projectId), () -> createQuestionsLocked(userId, projectId, recordIds));
  }

  static String finishLockKey(Long projectId) {
    return "finish:" + projectId;
  }

  /** 주인 ID만 읽는 가벼운 확인. 남의 프로젝트거나 없으면 NOT_FOUND. */
  private void checkProjectOwner(Long userId, Long projectId) {
    projectRepository
        .findUserIdById(projectId)
        .filter(userId::equals)
        .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
  }

  private FinishQuestions createQuestionsLocked(Long userId, Long projectId, List<Long> recordIds) {
    Project project =
        projectRepository
            .findByIdAndUserId(projectId, userId)
            .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
    QuestionOwner owner = QuestionOwner.project(projectId);

    List<QuestionView> existing = aiQuestionService.findAll(owner);
    if (!existing.isEmpty()) {
      return new FinishQuestions(false, FinishQuestionsResponse.from(existing));
    }

    List<ProjectRecord> records = selectedRecords(projectId, recordIds);
    List<NewQuestion> questions =
        finishQuestionGenerator.generate(project, records, loadAttachments(projectId, records));
    return new FinishQuestions(
        true, FinishQuestionsResponse.from(aiQuestionService.replace(owner, questions)));
  }

  /**
   * 33번: 포트폴리오 초안. 고른 기록과 답한(ANSWERED) 마치기 질문으로 만든다. 저장하지 않는다(저장은 34번).
   *
   * <p>DB에 쓰는 것이 없어서 AI가 실패해도 지울 것이 없다. 마치기 답을 고치고 다시 부르면 고친 답으로 다시 만든다(H-18). AI는 항목별 본문만 주고,
   * 제목·기간·역할 줄·항목 제목·빈칸 문구·blanks는 PortfolioMarkdown이 붙인다.
   *
   * <p>남의 프로젝트거나 없으면 NOT_FOUND. 고른 기록 중 이 프로젝트의 SAVED 기록이 아닌 것이 있어도 NOT_FOUND.
   */
  public PortfolioDraftResponse createPortfolioDraft(
      Long userId, Long projectId, List<Long> recordIds) {
    checkProjectOwner(userId, projectId);
    return aiLock.run(
        finishLockKey(projectId), () -> createPortfolioDraftLocked(userId, projectId, recordIds));
  }

  private PortfolioDraftResponse createPortfolioDraftLocked(
      Long userId, Long projectId, List<Long> recordIds) {
    Project project =
        projectRepository
            .findByIdAndUserId(projectId, userId)
            .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
    List<ProjectRecord> records = selectedRecords(projectId, recordIds);
    List<QuestionView> answered =
        aiQuestionService.findAll(QuestionOwner.project(projectId)).stream()
            .filter(q -> q.status() == QuestionStatus.ANSWERED)
            .toList();

    Map<String, String> bodies =
        portfolioDrafter.draft(project, records, answered, loadAttachments(projectId, records));
    return PortfolioMarkdown.build(project, bodies);
  }

  /** 프로젝트 산출물과 고른 기록의 첨부. 프로젝트·기록이 내 것인지는 부르는 쪽이 먼저 확인한다. 32·33번이 같이 쓴다. */
  private FinishAttachments loadAttachments(Long projectId, List<ProjectRecord> records) {
    List<Long> recordIds = records.stream().map(ProjectRecord::getId).toList();
    return new FinishAttachments(
        attachmentService.findAll(AttachmentOwner.project(projectId)),
        attachmentService.findAll(AttachmentOwner.Kind.PROJECT_RECORD, recordIds).values().stream()
            .flatMap(List::stream)
            .toList());
  }

  /** 고른 기록(중복은 합침)을 만든 순서로. 이 프로젝트의 SAVED 기록이 아닌 ID가 있으면 NOT_FOUND. 32·33번이 같이 쓴다. */
  private List<ProjectRecord> selectedRecords(Long projectId, List<Long> recordIds) {
    Set<Long> ids = new LinkedHashSet<>(recordIds);
    List<ProjectRecord> records =
        projectRecordRepository.findAllByProjectIdAndStatusAndIdInOrderByCreatedAtAscIdAsc(
            projectId, ProjectRecordStatus.SAVED, ids);
    if (records.size() != ids.size()) {
      throw new BusinessException(ErrorCode.NOT_FOUND);
    }
    return records;
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
