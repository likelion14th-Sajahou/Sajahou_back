package com.sajarok.domain.matching.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.sajarok.domain.matching.dto.EditedAnswerResponse;
import com.sajarok.domain.matching.entity.AnswerDraft;
import com.sajarok.domain.matching.entity.AnswerDraft.Segment;
import com.sajarok.domain.matching.entity.CoverLetterQuestion;
import com.sajarok.domain.matching.repository.CoverLetterQuestionRepository;
import com.sajarok.global.exception.BusinessException;
import com.sajarok.global.exception.ErrorCode;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class CoverLetterQuestionServiceTest {

  private static final Long USER_ID = 1L;
  private static final Long QUESTION_ID = 55L;

  @Mock private CoverLetterQuestionRepository coverLetterQuestionRepository;

  @InjectMocks private CoverLetterQuestionService coverLetterQuestionService;

  /** 초안("초안 문장." = 6글자)이 있는 문항. 초안 저장은 37번 몫이라 테스트에서만 필드를 직접 채운다. */
  private CoverLetterQuestion questionWithDraft() {
    CoverLetterQuestion question = CoverLetterQuestion.builder().seq(1).content("협업 경험").build();
    ReflectionTestUtils.setField(question, "id", QUESTION_ID);
    ReflectionTestUtils.setField(
        question,
        "answerDraft",
        new AnswerDraft(List.of(new Segment("text", "초안 문장.", 1, null, null)), List.of()));
    return question;
  }

  @Test
  @DisplayName("고친 답을 보내면 엔티티에 저장하고, 고친 답의 글자 수를 준다")
  void editAnswer_savesEditedAnswerAndCountsIt() {
    CoverLetterQuestion question = questionWithDraft();
    when(coverLetterQuestionRepository.findByIdAndUserId(QUESTION_ID, USER_ID))
        .thenReturn(Optional.of(question));

    EditedAnswerResponse response =
        coverLetterQuestionService.editAnswer(USER_ID, QUESTION_ID, "고친 답이에요");

    assertThat(question.getEditedAnswer()).isEqualTo("고친 답이에요");
    assertThat(response.questionId()).isEqualTo(QUESTION_ID);
    assertThat(response.editedAnswer()).isEqualTo("고친 답이에요");
    assertThat(response.charCount()).isEqualTo(7);
  }

  @Test
  @DisplayName("null을 보내면 고친 답을 지우고(초안으로 되돌림), 초안의 글자 수를 준다")
  void editAnswer_null_revertsToDraft() {
    CoverLetterQuestion question = questionWithDraft();
    question.editAnswer("예전에 고친 답");
    when(coverLetterQuestionRepository.findByIdAndUserId(QUESTION_ID, USER_ID))
        .thenReturn(Optional.of(question));

    EditedAnswerResponse response =
        coverLetterQuestionService.editAnswer(USER_ID, QUESTION_ID, null);

    assertThat(question.getEditedAnswer()).isNull();
    assertThat(response.editedAnswer()).isNull();
    assertThat(response.charCount()).isEqualTo(6);
  }

  @Test
  @DisplayName("charLimit을 넘는 답도 막지 않고 저장한다 (초과 안내는 FE 몫)")
  void editAnswer_overCharLimit_stillSaves() {
    CoverLetterQuestion question = questionWithDraft();
    ReflectionTestUtils.setField(question, "charLimit", 3);
    when(coverLetterQuestionRepository.findByIdAndUserId(QUESTION_ID, USER_ID))
        .thenReturn(Optional.of(question));

    EditedAnswerResponse response =
        coverLetterQuestionService.editAnswer(USER_ID, QUESTION_ID, "세 글자 넘음");

    assertThat(question.getEditedAnswer()).isEqualTo("세 글자 넘음");
    assertThat(response.charCount()).isEqualTo(7);
  }

  @Test
  @DisplayName("남의 문항이거나 없는 문항이면 NOT_FOUND")
  void editAnswer_notMine_throwsNotFound() {
    when(coverLetterQuestionRepository.findByIdAndUserId(QUESTION_ID, USER_ID))
        .thenReturn(Optional.empty());

    assertThatThrownBy(() -> coverLetterQuestionService.editAnswer(USER_ID, QUESTION_ID, "답"))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.NOT_FOUND);
  }

  @Test
  @DisplayName("초안(37번)을 만들기 전이면 INVALID_STATE이고 아무것도 바꾸지 않는다")
  void editAnswer_noDraft_throwsInvalidState() {
    CoverLetterQuestion question = CoverLetterQuestion.builder().seq(1).content("협업 경험").build();
    when(coverLetterQuestionRepository.findByIdAndUserId(QUESTION_ID, USER_ID))
        .thenReturn(Optional.of(question));

    assertThatThrownBy(() -> coverLetterQuestionService.editAnswer(USER_ID, QUESTION_ID, "답"))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.INVALID_STATE);
    assertThat(question.getEditedAnswer()).isNull();
  }
}
