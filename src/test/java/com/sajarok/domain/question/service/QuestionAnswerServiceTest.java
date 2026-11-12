package com.sajarok.domain.question.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.sajarok.domain.attachment.dto.AttachmentOwner;
import com.sajarok.domain.attachment.service.AttachmentService;
import com.sajarok.domain.project.service.ProjectRecordService;
import com.sajarok.domain.project.service.ProjectService;
import com.sajarok.domain.question.dto.AnswerQuestionRequest;
import com.sajarok.domain.question.dto.AnswerQuestionResponse;
import com.sajarok.domain.question.entity.AiQuestion;
import com.sajarok.domain.question.entity.QuestionOwner;
import com.sajarok.domain.question.entity.QuestionStatus;
import com.sajarok.domain.question.repository.AiQuestionRepository;
import com.sajarok.domain.retrospective.entity.Retrospective;
import com.sajarok.domain.retrospective.entity.RetrospectiveType;
import com.sajarok.domain.retrospective.repository.RetrospectiveRepository;
import com.sajarok.global.exception.BusinessException;
import com.sajarok.global.exception.ErrorCode;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 서비스 테스트: 18번에서 내 질문인지 확인하는 규칙과 답하기·건너뛰기 결과를 확인한다.
 *
 * <p>DB 없이 레포지토리와 희선님 서비스를 가짜(@Mock)로 바꾼다.
 */
@ExtendWith(MockitoExtension.class)
class QuestionAnswerServiceTest {

  private static final Long ME = 1L;
  private static final Long OTHER = 2L;

  @Mock private AiQuestionRepository aiQuestionRepository;
  @Mock private RetrospectiveRepository retrospectiveRepository;
  @Mock private ProjectRecordService projectRecordService;
  @Mock private ProjectService projectService;
  @Mock private AttachmentService attachmentService;

  @InjectMocks private QuestionAnswerService questionAnswerService;

  /** id가 901인 질문. DB가 없으니 id는 직접 넣는다 */
  private static AiQuestion question(QuestionOwner owner) {
    AiQuestion q = AiQuestion.create(owner, 1, "과정", null, null, "결론은 어떤 과정을 거쳐 나왔어요?");
    ReflectionTestUtils.setField(q, "id", 901L);
    return q;
  }

  /** 10번 회고의 주인을 정한다 */
  private void retrospective10OwnedBy(Long userId) {
    when(retrospectiveRepository.findById(10L))
        .thenReturn(Optional.of(Retrospective.start(userId, 5L, RetrospectiveType.SESSION)));
  }

  private static AnswerQuestionRequest request(QuestionStatus status, String answer) {
    return new AnswerQuestionRequest(status, answer, null);
  }

  @Test
  @DisplayName("내 회고 질문에 답하면 ANSWERED와 답을 돌려주고, save 없이 엔티티가 바뀐다")
  void answer_myRetrospectiveQuestion() {
    AiQuestion question = question(QuestionOwner.retrospective(10L));
    when(aiQuestionRepository.findById(901L)).thenReturn(Optional.of(question));
    retrospective10OwnedBy(ME);

    AnswerQuestionResponse response =
        questionAnswerService.answer(
            ME, 901L, request(QuestionStatus.ANSWERED, "쿼리가 21개에서 2개로 줄었어요"));

    assertThat(response.questionId()).isEqualTo(901L);
    assertThat(response.seq()).isEqualTo(1);
    assertThat(response.status()).isEqualTo(QuestionStatus.ANSWERED);
    assertThat(response.answer()).isEqualTo("쿼리가 21개에서 2개로 줄었어요");
    assertThat(question.getStatus()).isEqualTo(QuestionStatus.ANSWERED);
    verify(aiQuestionRepository, never()).save(any());
  }

  @Test
  @DisplayName("이미 답한 질문을 건너뛰면 답이 지워지고 SKIPPED가 된다")
  void skip_clearsPreviousAnswer() {
    AiQuestion question = question(QuestionOwner.retrospective(10L));
    question.answer("처음에 적은 답");
    when(aiQuestionRepository.findById(901L)).thenReturn(Optional.of(question));
    retrospective10OwnedBy(ME);

    AnswerQuestionResponse response =
        questionAnswerService.answer(ME, 901L, request(QuestionStatus.SKIPPED, null));

    assertThat(response.status()).isEqualTo(QuestionStatus.SKIPPED);
    assertThat(response.answer()).isNull();
  }

  @Test
  @DisplayName("없는 질문이면 NOT_FOUND이고, 주인 확인까지 가지 않는다")
  void missingQuestion_notFound() {
    when(aiQuestionRepository.findById(999L)).thenReturn(Optional.empty());

    assertThatThrownBy(
            () -> questionAnswerService.answer(ME, 999L, request(QuestionStatus.SKIPPED, null)))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.NOT_FOUND);
    verifyNoInteractions(retrospectiveRepository, projectRecordService, projectService);
  }

  @Test
  @DisplayName("남의 회고 질문이면 NOT_FOUND이고, 질문은 PENDING 그대로다")
  void otherUsersRetrospectiveQuestion_notFound() {
    AiQuestion question = question(QuestionOwner.retrospective(10L));
    when(aiQuestionRepository.findById(901L)).thenReturn(Optional.of(question));
    retrospective10OwnedBy(OTHER);

    assertThatThrownBy(
            () -> questionAnswerService.answer(ME, 901L, request(QuestionStatus.ANSWERED, "답")))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.NOT_FOUND);
    assertThat(question.getStatus()).isEqualTo(QuestionStatus.PENDING);
  }

  @Test
  @DisplayName("기록 질문은 ProjectRecordService.getOwnerId로 주인을 확인한다")
  void projectRecordQuestion_usesProjectRecordService() {
    AiQuestion question = question(QuestionOwner.projectRecord(77L));
    when(aiQuestionRepository.findById(901L)).thenReturn(Optional.of(question));
    when(projectRecordService.getOwnerId(77L)).thenReturn(ME);

    AnswerQuestionResponse response =
        questionAnswerService.answer(ME, 901L, request(QuestionStatus.ANSWERED, "의견이 갈렸어요"));

    assertThat(response.status()).isEqualTo(QuestionStatus.ANSWERED);
    verifyNoInteractions(retrospectiveRepository, projectService);
  }

  @Test
  @DisplayName("남의 프로젝트(마치기) 질문이면 NOT_FOUND")
  void otherUsersProjectQuestion_notFound() {
    AiQuestion question = question(QuestionOwner.project(3L));
    when(aiQuestionRepository.findById(901L)).thenReturn(Optional.of(question));
    when(projectService.getOwnerId(3L)).thenReturn(OTHER);

    assertThatThrownBy(
            () -> questionAnswerService.answer(ME, 901L, request(QuestionStatus.SKIPPED, null)))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.NOT_FOUND);
  }

  @Test
  @DisplayName("PENDING을 보내면 VALIDATION_ERROR")
  void pendingStatus_validationError() {
    AiQuestion question = question(QuestionOwner.retrospective(10L));
    when(aiQuestionRepository.findById(901L)).thenReturn(Optional.of(question));
    retrospective10OwnedBy(ME);

    assertThatThrownBy(
            () -> questionAnswerService.answer(ME, 901L, request(QuestionStatus.PENDING, null)))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.VALIDATION_ERROR);
  }

  @Test
  @DisplayName("ANSWERED인데 답이 비어 있으면 VALIDATION_ERROR이고, 질문은 PENDING 그대로다")
  void blankAnswer_validationError() {
    AiQuestion question = question(QuestionOwner.retrospective(10L));
    when(aiQuestionRepository.findById(901L)).thenReturn(Optional.of(question));
    retrospective10OwnedBy(ME);

    assertThatThrownBy(
            () -> questionAnswerService.answer(ME, 901L, request(QuestionStatus.ANSWERED, "  ")))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.VALIDATION_ERROR);
    assertThat(question.getStatus()).isEqualTo(QuestionStatus.PENDING);
  }

  // ---------- 18번 마치기 '산출물 요청' 첨부 ----------

  /** 3번 프로젝트 마치기 질문 (태그를 정한다) */
  private static AiQuestion finishQuestion(String tag) {
    AiQuestion q =
        AiQuestion.create(QuestionOwner.project(3L), 4, tag, null, null, "산출물을 첨부해 주세요.");
    ReflectionTestUtils.setField(q, "id", 980L);
    return q;
  }

  @Test
  @DisplayName("18번: 마치기 '산출물 요청' 질문에 attachmentIds를 보내면 프로젝트 산출물을 그 목록으로 통째로 바꾼다 (풀고 → 연결)")
  void artifactQuestion_replacesProjectAttachments() {
    when(aiQuestionRepository.findById(980L)).thenReturn(Optional.of(finishQuestion("산출물 요청")));
    when(projectService.getOwnerId(3L)).thenReturn(ME);

    AnswerQuestionResponse response =
        questionAnswerService.answer(
            ME, 980L, new AnswerQuestionRequest(QuestionStatus.ANSWERED, "발표 자료예요", List.of(610L)));

    assertThat(response.status()).isEqualTo(QuestionStatus.ANSWERED);
    InOrder order = inOrder(attachmentService);
    order.verify(attachmentService).disconnect(AttachmentOwner.project(3L));
    order.verify(attachmentService).connect(ME, List.of(610L), AttachmentOwner.project(3L));
  }

  @Test
  @DisplayName("18번: 산출물 질문이 아닌데 첨부를 보내면 400 VALIDATION_ERROR이고, 답도 첨부도 바뀌지 않는다")
  void otherQuestion_withAttachments_validationError() {
    AiQuestion question = finishQuestion("내 역할");
    when(aiQuestionRepository.findById(980L)).thenReturn(Optional.of(question));
    when(projectService.getOwnerId(3L)).thenReturn(ME);

    assertThatThrownBy(
            () ->
                questionAnswerService.answer(
                    ME,
                    980L,
                    new AnswerQuestionRequest(QuestionStatus.ANSWERED, "역할", List.of(610L))))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.VALIDATION_ERROR);
    assertThat(question.getStatus()).isEqualTo(QuestionStatus.PENDING);
    verifyNoInteractions(attachmentService);
  }

  @Test
  @DisplayName("18번: attachmentIds를 안 보내면 산출물 질문이어도 첨부는 그대로 둔다")
  void artifactQuestion_withoutAttachmentIds_keepsAttachments() {
    when(aiQuestionRepository.findById(980L)).thenReturn(Optional.of(finishQuestion("산출물 요청")));
    when(projectService.getOwnerId(3L)).thenReturn(ME);

    questionAnswerService.answer(ME, 980L, request(QuestionStatus.SKIPPED, null));

    verifyNoInteractions(attachmentService);
  }
}
