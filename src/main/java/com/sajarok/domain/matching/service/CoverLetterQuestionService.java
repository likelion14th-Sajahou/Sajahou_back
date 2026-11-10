package com.sajarok.domain.matching.service;

import com.sajarok.domain.matching.dto.EditedAnswerResponse;
import com.sajarok.domain.matching.entity.CoverLetterQuestion;
import com.sajarok.domain.matching.repository.CoverLetterQuestionRepository;
import com.sajarok.global.exception.BusinessException;
import com.sajarok.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CoverLetterQuestionService {

  private final CoverLetterQuestionRepository coverLetterQuestionRepository;

  /**
   * 38번: 직접 고친 답 저장. null이면 AI 초안으로 되돌린다.
   *
   * <p>남의 문항이거나 없으면 NOT_FOUND, 초안(37번)을 만들기 전이면 INVALID_STATE. 저장은 변경 감지로 한다.
   */
  @Transactional
  public EditedAnswerResponse editAnswer(Long userId, Long questionId, String editedAnswer) {
    CoverLetterQuestion question =
        coverLetterQuestionRepository
            .findByIdAndUserId(questionId, userId)
            .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
    if (question.getAnswerDraft() == null) {
      throw new BusinessException(ErrorCode.INVALID_STATE);
    }

    question.editAnswer(editedAnswer);
    return new EditedAnswerResponse(
        question.getId(), question.getEditedAnswer(), question.charCount());
  }
}
