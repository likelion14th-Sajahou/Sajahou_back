package com.sajarok.domain.project.service;

import com.sajarok.domain.project.entity.ProjectRecord;
import com.sajarok.domain.project.repository.ProjectRecordRepository;
import com.sajarok.domain.question.dto.NewQuestion;
import com.sajarok.domain.question.dto.QuestionView;
import com.sajarok.domain.question.entity.QuestionOwner;
import com.sajarok.domain.question.service.AiQuestionService;
import com.sajarok.global.exception.BusinessException;
import com.sajarok.global.exception.ErrorCode;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** 30번 저장 트랜잭션. 질문 교체와 자료 해시를 한 트랜잭션으로 묶는다(둘이 어긋나지 않게). */
@Component
@RequiredArgsConstructor
public class RecordQuestionWriter {

  private final ProjectRecordRepository projectRecordRepository;
  private final AiQuestionService aiQuestionService;

  /** 질문을 새것으로 바꾸고 해시를 남긴다. AI를 기다리는 사이 기록이 지워졌으면 NOT_FOUND. */
  @Transactional
  public List<QuestionView> replace(Long recordId, List<NewQuestion> questions, String hash) {
    findRecord(recordId).recordQuestionInput(hash);
    return aiQuestionService.replace(QuestionOwner.projectRecord(recordId), questions);
  }

  /** CONTINUE: 질문은 그대로 두고 해시만 지금 자료로 바꾼다(다음 AUTO에서 다시 묻지 않게). */
  @Transactional
  public void continueWith(Long recordId, String hash) {
    findRecord(recordId).recordQuestionInput(hash);
  }

  private ProjectRecord findRecord(Long recordId) {
    return projectRecordRepository
        .findById(recordId)
        .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
  }
}
