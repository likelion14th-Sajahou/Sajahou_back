package com.sajarok.domain.question.service;

import com.sajarok.domain.attachment.dto.AttachmentOwner;
import com.sajarok.domain.attachment.service.AttachmentService;
import com.sajarok.domain.project.service.ProjectRecordService;
import com.sajarok.domain.project.service.ProjectService;
import com.sajarok.domain.question.dto.AnswerQuestionRequest;
import com.sajarok.domain.question.dto.AnswerQuestionResponse;
import com.sajarok.domain.question.entity.AiQuestion;
import com.sajarok.domain.question.entity.QuestionOwner;
import com.sajarok.domain.question.repository.AiQuestionRepository;
import com.sajarok.domain.retrospective.entity.Retrospective;
import com.sajarok.domain.retrospective.repository.RetrospectiveRepository;
import com.sajarok.global.exception.BusinessException;
import com.sajarok.global.exception.ErrorCode;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class QuestionAnswerService {

  private final AiQuestionRepository aiQuestionRepository;
  private final RetrospectiveRepository retrospectiveRepository;
  private final ProjectRecordService projectRecordService;
  private final ProjectService projectService;
  private final AttachmentService attachmentService;

  /** 마치기(32번) 질문 중 첨부를 받는 질문의 태그 (FinishQuestionResult.TAGS) */
  static final String ARTIFACT_TAG = "산출물 요청";

  @Transactional
  public AnswerQuestionResponse answer(
      Long userId, Long questionId, AnswerQuestionRequest request) {
    AiQuestion question =
        aiQuestionRepository
            .findById(questionId)
            .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));

    if (!ownerIdOf(question.owner()).equals(userId)) {
      throw new BusinessException(ErrorCode.NOT_FOUND);
    }

    List<Long> attachmentIds = request.attachmentIds();
    boolean artifactQuestion = isArtifactQuestion(question);
    // 바꾸기 전에 확인: 첨부는 마치기 '산출물 요청' 질문에만 붙는다
    if (attachmentIds != null && !attachmentIds.isEmpty() && !artifactQuestion) {
      throw new BusinessException(ErrorCode.VALIDATION_ERROR, "첨부는 마치기 '산출물 요청' 질문에만 붙일 수 있어요.");
    }

    switch (request.status()) {
      case ANSWERED -> question.answer(request.answer());
      case SKIPPED -> question.skip();
      case PENDING -> throw new BusinessException(ErrorCode.VALIDATION_ERROR, "답할지 건너뛸지 골라 주세요.");
    }
    // 산출물 질문이면 보낸 목록으로 프로젝트 산출물을 통째로 바꾼다 (33번 포트폴리오 초안이 읽음).
    // 같은 트랜잭션이라 connect가 실패(남의 첨부 404 등)하면 답과 연결 해제도 함께 되돌려진다.
    if (attachmentIds != null && artifactQuestion) {
      AttachmentOwner project = AttachmentOwner.project(question.owner().id());
      attachmentService.disconnect(project);
      attachmentService.connect(userId, attachmentIds, project);
    }

    return AnswerQuestionResponse.from(question);
  }

  private static boolean isArtifactQuestion(AiQuestion question) {
    return question.owner().kind() == QuestionOwner.Kind.PROJECT
        && ARTIFACT_TAG.equals(question.getTag());
  }

  /** 질문 주인(회고·기록·프로젝트)의 회원 ID */
  private Long ownerIdOf(QuestionOwner owner) {
    return switch (owner.kind()) {
      case RETROSPECTIVE ->
          retrospectiveRepository
              .findById(owner.id())
              .map(Retrospective::getUserId)
              .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
      case PROJECT_RECORD -> projectRecordService.getOwnerId(owner.id());
      case PROJECT -> projectService.getOwnerId(owner.id());
    };
  }
}
