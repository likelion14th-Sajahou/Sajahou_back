package com.sajarok.domain.question.service;

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

    switch (request.status()) {
      case ANSWERED -> question.answer(request.answer());
      case SKIPPED -> question.skip();
      case PENDING -> throw new BusinessException(ErrorCode.VALIDATION_ERROR, "답할지 건너뛸지 골라 주세요.");
    }
    // TODO(첨부 담당): request.attachmentIds()를 마치기 '산출물 요청' 질문에 연결. AttachmentService가 생기면.

    return AnswerQuestionResponse.from(question);
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
