package com.sajarok.domain.retrospective.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.sajarok.domain.question.dto.QuestionView;
import com.sajarok.domain.question.entity.QuestionOwner;
import com.sajarok.domain.question.entity.QuestionStatus;
import com.sajarok.domain.question.service.AiQuestionService;
import com.sajarok.domain.retrospective.dto.RetrospectiveCompleteResponse;
import com.sajarok.domain.retrospective.dto.RetrospectiveDetailResponse;
import com.sajarok.domain.retrospective.dto.RetrospectiveDraftRequest;
import com.sajarok.domain.retrospective.dto.RetrospectiveProgressResponse;
import com.sajarok.domain.retrospective.dto.RetrospectiveRestResponse;
import com.sajarok.domain.retrospective.dto.RetrospectiveStartRequest;
import com.sajarok.domain.retrospective.dto.RetrospectiveState;
import com.sajarok.domain.retrospective.entity.AssignmentMode;
import com.sajarok.domain.retrospective.entity.Retrospective;
import com.sajarok.domain.retrospective.entity.RetrospectiveStatus;
import com.sajarok.domain.retrospective.entity.RetrospectiveType;
import com.sajarok.domain.retrospective.repository.RetrospectiveRepository;
import com.sajarok.domain.retrospective.service.RetrospectiveService.StartResult;
import com.sajarok.domain.storycard.dto.StoryCardDetailResponse;
import com.sajarok.domain.storycard.entity.StoryCardStatus;
import com.sajarok.domain.storycard.entity.StoryCardType;
import com.sajarok.domain.storycard.service.StoryCardService;
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

/**
 * 서비스 테스트: 12번 회고 시작(이미 있으면 그대로, 휴회, 과제 회고 조건), 13번 상세, 14번 임시저장, 15번 휴회 표시·해제, 16번 처음부터, 20번 저장
 * 규칙을 확인한다.
 *
 * <p>DB 없이 레포지토리와 AI 질문·카드 서비스를 가짜(@Mock)로 바꾼다. 주차 확인은 주차 모듈이 생기면 더한다.
 */
@ExtendWith(MockitoExtension.class)
class RetrospectiveServiceTest {

  private static final Long ME = 1L;
  private static final Long WEEK = 10L;

  @Mock private RetrospectiveRepository retrospectiveRepository;
  @Mock private AiQuestionService aiQuestionService;
  @Mock private StoryCardService storyCardService;

  @InjectMocks private RetrospectiveService retrospectiveService;

  private static RetrospectiveStartRequest request(RetrospectiveType type, Boolean rested) {
    return new RetrospectiveStartRequest(WEEK, type, rested);
  }

  /** 10주차 세션 회고(id 101). 과제 방식을 정해 둔다 */
  private static Retrospective sessionWith(AssignmentMode mode) {
    Retrospective session = Retrospective.start(ME, WEEK, RetrospectiveType.SESSION);
    ReflectionTestUtils.setField(session, "id", 101L);
    ReflectionTestUtils.setField(session, "assignmentMode", mode);
    return session;
  }

  /** save는 받은 객체에 id를 채워 그대로 돌려준다 (DB가 하는 일을 흉내) */
  private void saveReturnsWithId(Long id) {
    when(retrospectiveRepository.save(any(Retrospective.class)))
        .thenAnswer(
            invocation -> {
              Retrospective saved = invocation.getArgument(0);
              ReflectionTestUtils.setField(saved, "id", id);
              return saved;
            });
  }

  @Test
  @DisplayName("12번: 처음 시작하면 새로 만들고(created) IN_PROGRESS·2단계로 준다")
  void start_new() {
    when(retrospectiveRepository.findByUserIdAndWeekIdAndType(ME, WEEK, RetrospectiveType.SESSION))
        .thenReturn(Optional.empty());
    saveReturnsWithId(101L);

    StartResult result = retrospectiveService.start(ME, request(RetrospectiveType.SESSION, null));

    assertThat(result.created()).isTrue();
    assertThat(result.response().id()).isEqualTo(101L);
    assertThat(result.response().state()).isEqualTo(RetrospectiveState.IN_PROGRESS);
    assertThat(result.response().currentStep()).isEqualTo(2);
  }

  @Test
  @DisplayName("12번: 이미 있으면 새로 만들지 않고 기존 회고를 그대로 준다")
  void start_existing() {
    when(retrospectiveRepository.findByUserIdAndWeekIdAndType(ME, WEEK, RetrospectiveType.SESSION))
        .thenReturn(Optional.of(sessionWith(null)));

    StartResult result = retrospectiveService.start(ME, request(RetrospectiveType.SESSION, false));

    assertThat(result.created()).isFalse();
    assertThat(result.response().id()).isEqualTo(101L);
    verify(retrospectiveRepository, never()).save(any(Retrospective.class));
  }

  @Test
  @DisplayName("12번: 시작 전에 휴회하면 RESTED이고 단계가 없다")
  void start_rested() {
    when(retrospectiveRepository.findByUserIdAndWeekIdAndType(ME, WEEK, RetrospectiveType.SESSION))
        .thenReturn(Optional.empty());
    saveReturnsWithId(101L);

    StartResult result = retrospectiveService.start(ME, request(RetrospectiveType.SESSION, true));

    assertThat(result.response().state()).isEqualTo(RetrospectiveState.RESTED);
    assertThat(result.response().currentStep()).isNull();
  }

  @Test
  @DisplayName("12번: 세션 회고에서 과제를 '나중에 쓸게요(LATER)'로 골랐으면 과제 회고를 만든다")
  void start_assignment_afterLater() {
    when(retrospectiveRepository.findByUserIdAndWeekIdAndType(
            ME, WEEK, RetrospectiveType.ASSIGNMENT))
        .thenReturn(Optional.empty());
    when(retrospectiveRepository.findByUserIdAndWeekIdAndType(ME, WEEK, RetrospectiveType.SESSION))
        .thenReturn(Optional.of(sessionWith(AssignmentMode.LATER)));
    saveReturnsWithId(205L);

    StartResult result =
        retrospectiveService.start(ME, request(RetrospectiveType.ASSIGNMENT, null));

    assertThat(result.created()).isTrue();
    assertThat(result.response().type()).isEqualTo(RetrospectiveType.ASSIGNMENT);
    assertThat(result.response().state()).isEqualTo(RetrospectiveState.IN_PROGRESS);
  }

  @Test
  @DisplayName("12번: 그 주 세션 회고가 없으면 과제 회고는 409 INVALID_STATE")
  void start_assignment_noSession() {
    when(retrospectiveRepository.findByUserIdAndWeekIdAndType(
            ME, WEEK, RetrospectiveType.ASSIGNMENT))
        .thenReturn(Optional.empty());
    when(retrospectiveRepository.findByUserIdAndWeekIdAndType(ME, WEEK, RetrospectiveType.SESSION))
        .thenReturn(Optional.empty());

    assertThatThrownBy(
            () -> retrospectiveService.start(ME, request(RetrospectiveType.ASSIGNMENT, null)))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.INVALID_STATE);
    verify(retrospectiveRepository, never()).save(any(Retrospective.class));
  }

  @Test
  @DisplayName("12번: 세션 회고에서 과제를 함께 기록(RECORD)했으면 과제 회고는 409 INVALID_STATE")
  void start_assignment_notLater() {
    when(retrospectiveRepository.findByUserIdAndWeekIdAndType(
            ME, WEEK, RetrospectiveType.ASSIGNMENT))
        .thenReturn(Optional.empty());
    when(retrospectiveRepository.findByUserIdAndWeekIdAndType(ME, WEEK, RetrospectiveType.SESSION))
        .thenReturn(Optional.of(sessionWith(AssignmentMode.RECORD)));

    assertThatThrownBy(
            () -> retrospectiveService.start(ME, request(RetrospectiveType.ASSIGNMENT, null)))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.INVALID_STATE);
  }

  @Test
  @DisplayName("12번: 과제 회고는 휴회할 수 없다 (400 VALIDATION_ERROR)")
  void start_assignment_rested() {
    when(retrospectiveRepository.findByUserIdAndWeekIdAndType(
            ME, WEEK, RetrospectiveType.ASSIGNMENT))
        .thenReturn(Optional.empty());

    assertThatThrownBy(
            () -> retrospectiveService.start(ME, request(RetrospectiveType.ASSIGNMENT, true)))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.VALIDATION_ERROR);
  }

  /** 15번 준비: 3단계까지 쓰던 내 세션 회고(id 101) */
  private Retrospective mySessionAtStep3() {
    Retrospective session = sessionWith(AssignmentMode.RECORD);
    ReflectionTestUtils.setField(session, "currentStep", 3);
    ReflectionTestUtils.setField(session, "memo", "이번 주 고민");
    when(retrospectiveRepository.findByIdAndUserId(101L, ME)).thenReturn(Optional.of(session));
    return session;
  }

  @Test
  @DisplayName("15번: 휴회로 표시하면 RESTED가 되고, 쓰던 내용과 단계는 그대로다")
  void rest_on_keepsContent() {
    Retrospective session = mySessionAtStep3();

    RetrospectiveRestResponse response = retrospectiveService.changeRested(ME, 101L, true);

    assertThat(response.id()).isEqualTo(101L);
    assertThat(response.state()).isEqualTo(RetrospectiveState.RESTED);
    assertThat(session.getMemo()).isEqualTo("이번 주 고민");
    assertThat(session.getCurrentStep()).isEqualTo(3);
  }

  @Test
  @DisplayName("15번: 3단계까지 쓰다 휴회했던 회고를 해제하면 3단계 그대로 이어 쓴다")
  void rest_off_keepsStep() {
    Retrospective session = mySessionAtStep3();
    session.changeRested(true);

    RetrospectiveRestResponse response = retrospectiveService.changeRested(ME, 101L, false);

    assertThat(response.state()).isEqualTo(RetrospectiveState.IN_PROGRESS);
    assertThat(session.getCurrentStep()).isEqualTo(3);
  }

  @Test
  @DisplayName("15번: 시작 전에 휴회했던 회고를 해제하면 2단계부터 시작한다")
  void rest_off_fromRestedStart_startsAtStep2() {
    Retrospective rested = Retrospective.startRested(ME, WEEK);
    ReflectionTestUtils.setField(rested, "id", 101L);
    when(retrospectiveRepository.findByIdAndUserId(101L, ME)).thenReturn(Optional.of(rested));

    RetrospectiveRestResponse response = retrospectiveService.changeRested(ME, 101L, false);

    assertThat(response.state()).isEqualTo(RetrospectiveState.IN_PROGRESS);
    assertThat(rested.getCurrentStep()).isEqualTo(2);
  }

  @Test
  @DisplayName("15번: 과제 회고는 휴회할 수 없다 (409 INVALID_STATE)")
  void rest_assignment_invalidState() {
    Retrospective assignment = Retrospective.start(ME, WEEK, RetrospectiveType.ASSIGNMENT);
    when(retrospectiveRepository.findByIdAndUserId(205L, ME)).thenReturn(Optional.of(assignment));

    assertThatThrownBy(() -> retrospectiveService.changeRested(ME, 205L, true))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.INVALID_STATE);
  }

  @Test
  @DisplayName("15번: 완료한 회고는 휴회로 바꿀 수 없다 (409 INVALID_STATE)")
  void rest_completed_invalidState() {
    Retrospective session = mySessionAtStep3();
    session.complete(java.time.LocalDateTime.of(2026, 11, 8, 21, 0));

    assertThatThrownBy(() -> retrospectiveService.changeRested(ME, 101L, true))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.INVALID_STATE);
  }

  @Test
  @DisplayName("15번: 없거나 남의 회고면 NOT_FOUND")
  void rest_notMine_notFound() {
    when(retrospectiveRepository.findByIdAndUserId(999L, ME)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> retrospectiveService.changeRested(ME, 999L, true))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.NOT_FOUND);
  }

  @Test
  @DisplayName("16번: 처음부터 하면 2단계 자료와 자료 해시를 비우고 2단계로 돌아가며, AI 질문과 카드 초안도 지운다")
  void reset_clearsMaterialsAndDeletesQuestionsAndCard() {
    Retrospective session = mySessionAtStep3();
    ReflectionTestUtils.setField(session, "questionInputHash", "a".repeat(64));

    RetrospectiveProgressResponse response = retrospectiveService.reset(ME, 101L);

    assertThat(response.id()).isEqualTo(101L);
    assertThat(response.state()).isEqualTo(RetrospectiveState.IN_PROGRESS);
    assertThat(response.currentStep()).isEqualTo(2);
    assertThat(response.lastSavedAt()).isNull();
    assertThat(session.getMemo()).isNull();
    assertThat(session.getAssignmentMode()).isNull();
    assertThat(session.getQuestionInputHash()).isNull();
    verify(aiQuestionService).deleteAll(QuestionOwner.retrospective(101L));
    verify(storyCardService).deleteByRetrospective(101L);
  }

  @Test
  @DisplayName("16번: 완료한 회고는 409 INVALID_STATE이고, 질문·카드는 하나도 지우지 않는다")
  void reset_completed_deletesNothing() {
    Retrospective session = mySessionAtStep3();
    session.complete(java.time.LocalDateTime.of(2026, 11, 8, 21, 0));

    assertThatThrownBy(() -> retrospectiveService.reset(ME, 101L))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.INVALID_STATE);
    verifyNoInteractions(aiQuestionService, storyCardService);
  }

  @Test
  @DisplayName("16번: 없거나 남의 회고면 NOT_FOUND이고, 아무것도 지우지 않는다")
  void reset_notMine_notFound() {
    when(retrospectiveRepository.findByIdAndUserId(999L, ME)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> retrospectiveService.reset(ME, 999L))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.NOT_FOUND);
    verifyNoInteractions(aiQuestionService, storyCardService);
  }

  /** 13번 준비: 회고 101의 첫 질문 */
  private static QuestionView firstQuestion() {
    return new QuestionView(
        901L, 1, "근거와 숫자", null, null, "쿼리 수가 실제로 얼마나 줄었나요?", null, QuestionStatus.PENDING);
  }

  /** 13번 준비: 회고 101의 카드 초안 (필요한 칸만) */
  private static StoryCardDetailResponse draftCard() {
    return new StoryCardDetailResponse(
        301L,
        StoryCardType.SESSION,
        StoryCardStatus.DRAFT,
        null,
        null,
        null,
        "N+1 해결",
        null,
        null,
        null,
        null,
        null,
        List.of(),
        List.of(),
        null,
        null,
        List.of(),
        null);
  }

  @Test
  @DisplayName("13번: 상세는 회고 자료와 이 회고의 질문·카드를 한 번에 모아 준다 (주차는 null, 첨부는 빈 목록)")
  void detail_collectsQuestionsAndCard() {
    mySessionAtStep3();
    when(aiQuestionService.findAll(QuestionOwner.retrospective(101L)))
        .thenReturn(List.of(firstQuestion()));
    when(storyCardService.findDetailByRetrospective(101L)).thenReturn(Optional.of(draftCard()));

    RetrospectiveDetailResponse response = retrospectiveService.getDetail(ME, 101L);

    assertThat(response.id()).isEqualTo(101L);
    assertThat(response.state()).isEqualTo(RetrospectiveState.IN_PROGRESS);
    assertThat(response.isRested()).isFalse();
    assertThat(response.currentStep()).isEqualTo(3);
    assertThat(response.materials().memo()).isEqualTo("이번 주 고민");
    assertThat(response.questions()).extracting(QuestionView::questionId).containsExactly(901L);
    assertThat(response.card().cardId()).isEqualTo(301L);
    assertThat(response.week()).isNull();
    assertThat(response.attachments()).isEmpty();
  }

  @Test
  @DisplayName("13번: 4단계 전이라 카드가 없으면 card는 null이다")
  void detail_noCard() {
    mySessionAtStep3();
    when(aiQuestionService.findAll(QuestionOwner.retrospective(101L))).thenReturn(List.of());
    when(storyCardService.findDetailByRetrospective(101L)).thenReturn(Optional.empty());

    RetrospectiveDetailResponse response = retrospectiveService.getDetail(ME, 101L);

    assertThat(response.card()).isNull();
    assertThat(response.questions()).isEmpty();
  }

  @Test
  @DisplayName("13번: 없거나 남의 회고면 NOT_FOUND이고, 질문·카드는 찾지 않는다")
  void detail_notMine_notFound() {
    when(retrospectiveRepository.findByIdAndUserId(999L, ME)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> retrospectiveService.getDetail(ME, 999L))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.NOT_FOUND);
    verifyNoInteractions(aiQuestionService, storyCardService);
  }

  @Test
  @DisplayName("14번: 보낸 칸만 바뀐다. null을 보낸 칸은 비우고, 안 보낸 칸은 그대로 두고, 저장 시각을 남긴다")
  void draft_changesOnlySentFields() {
    Retrospective session = mySessionAtStep3();
    ReflectionTestUtils.setField(session, "practiceContent", "실습 내용");
    RetrospectiveDraftRequest request = new RetrospectiveDraftRequest();
    request.setCurrentStep(4);
    request.setMemo(null);
    request.setAssignmentMode(AssignmentMode.LATER);

    RetrospectiveProgressResponse response = retrospectiveService.saveDraft(ME, 101L, request);

    assertThat(session.getMemo()).isNull();
    assertThat(session.getAssignmentMode()).isEqualTo(AssignmentMode.LATER);
    assertThat(session.getPracticeContent()).isEqualTo("실습 내용");
    assertThat(response.currentStep()).isEqualTo(4);
    assertThat(response.lastSavedAt()).isNotNull();
  }

  @Test
  @DisplayName("14번: '나중에 이어쓰기'처럼 단계만 보내면 자료는 하나도 바뀌지 않는다")
  void draft_stepOnly_keepsMaterials() {
    Retrospective session = mySessionAtStep3();
    RetrospectiveDraftRequest request = new RetrospectiveDraftRequest();
    request.setCurrentStep(3);

    retrospectiveService.saveDraft(ME, 101L, request);

    assertThat(session.getMemo()).isEqualTo("이번 주 고민");
    assertThat(session.getAssignmentMode()).isEqualTo(AssignmentMode.RECORD);
    assertThat(session.getLastSavedAt()).isNotNull();
  }

  @Test
  @DisplayName("14번: 과제 회고에 과제 작성 방식을 보내면 400 VALIDATION_ERROR이고, 아무것도 바뀌지 않는다")
  void draft_assignmentModeOnAssignment_validationError() {
    Retrospective assignment = Retrospective.start(ME, WEEK, RetrospectiveType.ASSIGNMENT);
    when(retrospectiveRepository.findByIdAndUserId(205L, ME)).thenReturn(Optional.of(assignment));
    RetrospectiveDraftRequest request = new RetrospectiveDraftRequest();
    request.setMemo("메모");
    request.setAssignmentMode(AssignmentMode.LATER);

    assertThatThrownBy(() -> retrospectiveService.saveDraft(ME, 205L, request))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.VALIDATION_ERROR);
    assertThat(assignment.getMemo()).isNull();
  }

  @Test
  @DisplayName("14번: 완료한 회고도 고칠 수 있다")
  void draft_completed_allowed() {
    Retrospective session = mySessionAtStep3();
    session.complete(java.time.LocalDateTime.of(2026, 11, 8, 21, 0));
    RetrospectiveDraftRequest request = new RetrospectiveDraftRequest();
    request.setMemo("완료 후에 고친 메모");

    RetrospectiveProgressResponse response = retrospectiveService.saveDraft(ME, 101L, request);

    assertThat(session.getMemo()).isEqualTo("완료 후에 고친 메모");
    assertThat(response.state()).isEqualTo(RetrospectiveState.COMPLETED);
  }

  @Test
  @DisplayName("20번: 저장하면 카드를 공개하고 회고를 완료한다. 과제를 함께 기록(RECORD)했으면 남은 과제는 없다")
  void complete_publishesCardAndCompletes() {
    Retrospective session = mySessionAtStep3();
    when(storyCardService.publishRetrospectiveCard(101L)).thenReturn(301L);

    RetrospectiveCompleteResponse response = retrospectiveService.complete(ME, 101L);

    assertThat(response.state()).isEqualTo(RetrospectiveState.COMPLETED);
    assertThat(response.cardId()).isEqualTo(301L);
    assertThat(response.isLate()).isFalse();
    assertThat(response.assignmentPending()).isNull();
    assertThat(session.getCompletedAt()).isNotNull();
  }

  @Test
  @DisplayName("20번: 과제를 '나중에 쓸게요(LATER)'로 골랐으면 assignmentPending을 채운다")
  void complete_assignmentLater_pending() {
    Retrospective session = mySessionAtStep3();
    ReflectionTestUtils.setField(session, "assignmentMode", AssignmentMode.LATER);
    when(storyCardService.publishRetrospectiveCard(101L)).thenReturn(301L);

    RetrospectiveCompleteResponse response = retrospectiveService.complete(ME, 101L);

    assertThat(response.assignmentPending()).isNotNull();
  }

  @Test
  @DisplayName("20번: 카드 초안이 없으면(19번 전) 409 INVALID_STATE이고, 회고는 완료되지 않는다")
  void complete_noCard_notCompleted() {
    Retrospective session = mySessionAtStep3();
    when(storyCardService.publishRetrospectiveCard(101L))
        .thenThrow(new BusinessException(ErrorCode.INVALID_STATE));

    assertThatThrownBy(() -> retrospectiveService.complete(ME, 101L))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.INVALID_STATE);
    assertThat(session.getCompletedAt()).isNull();
    assertThat(session.getStatus()).isEqualTo(RetrospectiveStatus.IN_PROGRESS);
  }

  @Test
  @DisplayName("20번: 완료한 회고를 다시 저장해도 처음 완료 시각은 그대로다 (지각 판정이 바뀌지 않게)")
  void complete_again_keepsFirstCompletedAt() {
    Retrospective session = mySessionAtStep3();
    java.time.LocalDateTime first = java.time.LocalDateTime.of(2026, 11, 8, 21, 0);
    session.complete(first);
    when(storyCardService.publishRetrospectiveCard(101L)).thenReturn(301L);

    retrospectiveService.complete(ME, 101L);

    assertThat(session.getCompletedAt()).isEqualTo(first);
  }

  @Test
  @DisplayName("20번: 없거나 남의 회고면 NOT_FOUND이고, 카드는 건드리지 않는다")
  void complete_notMine_notFound() {
    when(retrospectiveRepository.findByIdAndUserId(999L, ME)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> retrospectiveService.complete(ME, 999L))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.NOT_FOUND);
    verifyNoInteractions(storyCardService);
  }
}
