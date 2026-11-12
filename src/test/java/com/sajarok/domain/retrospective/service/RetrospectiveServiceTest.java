package com.sajarok.domain.retrospective.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.sajarok.domain.attachment.dto.AttachmentOwner;
import com.sajarok.domain.attachment.dto.AttachmentView;
import com.sajarok.domain.attachment.entity.AttachmentCategory;
import com.sajarok.domain.attachment.entity.AttachmentType;
import com.sajarok.domain.attachment.entity.ExtractStatus;
import com.sajarok.domain.attachment.entity.LinkType;
import com.sajarok.domain.attachment.service.AttachmentService;
import com.sajarok.domain.question.dto.QuestionView;
import com.sajarok.domain.question.entity.QuestionOwner;
import com.sajarok.domain.question.entity.QuestionStatus;
import com.sajarok.domain.question.service.AiQuestionService;
import com.sajarok.domain.retrospective.dto.HomeRetroSummary;
import com.sajarok.domain.retrospective.dto.RetrospectiveCompleteResponse;
import com.sajarok.domain.retrospective.dto.RetrospectiveDetailResponse;
import com.sajarok.domain.retrospective.dto.RetrospectiveDraftRequest;
import com.sajarok.domain.retrospective.dto.RetrospectiveProgressResponse;
import com.sajarok.domain.retrospective.dto.RetrospectiveRestResponse;
import com.sajarok.domain.retrospective.dto.RetrospectiveStartRequest;
import com.sajarok.domain.retrospective.dto.RetrospectiveState;
import com.sajarok.domain.retrospective.dto.WeekRetroStatus;
import com.sajarok.domain.retrospective.entity.AssignmentMode;
import com.sajarok.domain.retrospective.entity.Retrospective;
import com.sajarok.domain.retrospective.entity.RetrospectiveStatus;
import com.sajarok.domain.retrospective.entity.RetrospectiveType;
import com.sajarok.domain.retrospective.repository.RetrospectiveRepository;
import com.sajarok.domain.retrospective.service.RetrospectiveService.StartResult;
import com.sajarok.domain.storycard.dto.CardSnapshot;
import com.sajarok.domain.storycard.dto.StoryCardDetailResponse;
import com.sajarok.domain.storycard.entity.StoryCardStatus;
import com.sajarok.domain.storycard.entity.StoryCardType;
import com.sajarok.domain.storycard.service.StoryCardService;
import com.sajarok.global.exception.BusinessException;
import com.sajarok.global.exception.ErrorCode;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 서비스 테스트: 12번 회고 시작(이미 있으면 그대로, 휴회, 과제 회고 조건), 13번 상세, 14번 임시저장, 15번 휴회 표시·해제, 16번 처음부터, 20번 저장
 * 규칙을 확인한다.
 *
 * <p>DB 없이 레포지토리와 AI 질문·카드·첨부 서비스를 가짜(@Mock)로 바꾼다. 주차 확인은 주차 모듈이 생기면 더한다.
 */
@ExtendWith(MockitoExtension.class)
class RetrospectiveServiceTest {

  private static final Long ME = 1L;
  private static final Long WEEK = 10L;
  private static final AttachmentOwner RETRO_101 = AttachmentOwner.retrospective(101L);

  @Mock private RetrospectiveRepository retrospectiveRepository;
  @Mock private AiQuestionService aiQuestionService;
  @Mock private StoryCardService storyCardService;
  @Mock private AttachmentService attachmentService;

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
  @DisplayName("16번: 처음부터 하면 2단계 자료와 자료 해시를 비우고 2단계로 돌아가며, AI 질문·카드 초안·첨부도 지운다")
  void reset_clearsMaterialsAndDeletesQuestionsCardAndAttachments() {
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
    verify(attachmentService).deleteAll(RETRO_101);
  }

  @Test
  @DisplayName("16번: 완료한 회고는 409 INVALID_STATE이고, 질문·카드·첨부는 하나도 지우지 않는다")
  void reset_completed_deletesNothing() {
    Retrospective session = mySessionAtStep3();
    session.complete(java.time.LocalDateTime.of(2026, 11, 8, 21, 0));

    assertThatThrownBy(() -> retrospectiveService.reset(ME, 101L))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.INVALID_STATE);
    verifyNoInteractions(aiQuestionService, storyCardService, attachmentService);
  }

  @Test
  @DisplayName("16번: 없거나 남의 회고면 NOT_FOUND이고, 아무것도 지우지 않는다")
  void reset_notMine_notFound() {
    when(retrospectiveRepository.findByIdAndUserId(999L, ME)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> retrospectiveService.reset(ME, 999L))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.NOT_FOUND);
    verifyNoInteractions(aiQuestionService, storyCardService, attachmentService);
  }

  /** 13번 준비: 회고 101의 첫 질문 */
  private static QuestionView firstQuestion() {
    return new QuestionView(
        901L, 1, "근거와 숫자", null, null, "쿼리 수가 실제로 얼마나 줄었나요?", null, QuestionStatus.PENDING);
  }

  /** 13번 준비: 회고 101에 붙은 GitHub 링크 */
  private static AttachmentView githubLink() {
    return new AttachmentView(
        501L,
        AttachmentType.LINK,
        AttachmentCategory.ASSIGNMENT,
        null,
        "https://github.com/me/assignment",
        LinkType.GITHUB,
        null,
        null,
        ExtractStatus.DONE);
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
  @DisplayName("13번: 상세는 회고 자료와 이 회고의 질문·첨부·카드를 한 번에 모아 준다 (주차는 아직 null)")
  void detail_collectsQuestionsAttachmentsAndCard() {
    mySessionAtStep3();
    when(aiQuestionService.findAll(QuestionOwner.retrospective(101L)))
        .thenReturn(List.of(firstQuestion()));
    when(attachmentService.findAll(RETRO_101)).thenReturn(List.of(githubLink()));
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
    assertThat(response.attachments())
        .extracting(AttachmentView::attachmentId, AttachmentView::linkType)
        .containsExactly(org.assertj.core.groups.Tuple.tuple(501L, LinkType.GITHUB));
  }

  @Test
  @DisplayName("13번: 4단계 전이라 카드가 없으면 card는 null, 첨부가 없으면 빈 목록이다")
  void detail_noCard() {
    mySessionAtStep3();
    when(aiQuestionService.findAll(QuestionOwner.retrospective(101L))).thenReturn(List.of());
    when(storyCardService.findDetailByRetrospective(101L)).thenReturn(Optional.empty());

    RetrospectiveDetailResponse response = retrospectiveService.getDetail(ME, 101L);

    assertThat(response.card()).isNull();
    assertThat(response.questions()).isEmpty();
    assertThat(response.attachments()).isEmpty();
  }

  @Test
  @DisplayName("13번: 없거나 남의 회고면 NOT_FOUND이고, 질문·첨부·카드는 찾지 않는다")
  void detail_notMine_notFound() {
    when(retrospectiveRepository.findByIdAndUserId(999L, ME)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> retrospectiveService.getDetail(ME, 999L))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.NOT_FOUND);
    verifyNoInteractions(aiQuestionService, storyCardService, attachmentService);
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
  @DisplayName("14번: '나중에 이어쓰기'처럼 단계만 보내면 자료와 첨부는 하나도 바뀌지 않는다")
  void draft_stepOnly_keepsMaterials() {
    Retrospective session = mySessionAtStep3();
    RetrospectiveDraftRequest request = new RetrospectiveDraftRequest();
    request.setCurrentStep(3);

    retrospectiveService.saveDraft(ME, 101L, request);

    assertThat(session.getMemo()).isEqualTo("이번 주 고민");
    assertThat(session.getAssignmentMode()).isEqualTo(AssignmentMode.RECORD);
    assertThat(session.getLastSavedAt()).isNotNull();
    verifyNoInteractions(attachmentService);
  }

  @Test
  @DisplayName("14번: 과제 회고에 과제 작성 방식을 보내면 400 VALIDATION_ERROR이고, 자료도 첨부도 바뀌지 않는다")
  void draft_assignmentModeOnAssignment_validationError() {
    Retrospective assignment = Retrospective.start(ME, WEEK, RetrospectiveType.ASSIGNMENT);
    when(retrospectiveRepository.findByIdAndUserId(205L, ME)).thenReturn(Optional.of(assignment));
    RetrospectiveDraftRequest request = new RetrospectiveDraftRequest();
    request.setMemo("메모");
    request.setAssignmentMode(AssignmentMode.LATER);
    request.setAttachmentIds(List.of(501L));

    assertThatThrownBy(() -> retrospectiveService.saveDraft(ME, 205L, request))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.VALIDATION_ERROR);
    assertThat(assignment.getMemo()).isNull();
    verifyNoInteractions(attachmentService);
  }

  @Test
  @DisplayName("14번: attachmentIds를 보내면 그 목록으로 통째로 바꾼다 (기존 연결을 다 풀고 → 보낸 것만 연결)")
  void draft_attachmentIds_replacesAll() {
    mySessionAtStep3();
    RetrospectiveDraftRequest request = new RetrospectiveDraftRequest();
    request.setAttachmentIds(List.of(501L, 502L));

    retrospectiveService.saveDraft(ME, 101L, request);

    InOrder order = inOrder(attachmentService);
    order.verify(attachmentService).disconnect(RETRO_101);
    order.verify(attachmentService).connect(ME, List.of(501L, 502L), RETRO_101);
  }

  @Test
  @DisplayName("14번: attachmentIds를 null로 보내면 연결만 다 풀린다 (connect는 null이면 아무것도 안 함)")
  void draft_attachmentIdsNull_disconnectsAll() {
    mySessionAtStep3();
    RetrospectiveDraftRequest request = new RetrospectiveDraftRequest();
    request.setAttachmentIds(null);

    retrospectiveService.saveDraft(ME, 101L, request);

    verify(attachmentService).disconnect(RETRO_101);
    verify(attachmentService).connect(ME, null, RETRO_101);
  }

  @Test
  @DisplayName("14번: 남의 첨부거나 없는 첨부면 NOT_FOUND가 그대로 나간다 (실제로는 @Transactional이 회고 변경·연결 해제를 함께 되돌림)")
  void draft_attachmentNotMine_notFound() {
    mySessionAtStep3();
    RetrospectiveDraftRequest request = new RetrospectiveDraftRequest();
    request.setAttachmentIds(List.of(999L));
    doThrow(new BusinessException(ErrorCode.NOT_FOUND))
        .when(attachmentService)
        .connect(ME, List.of(999L), RETRO_101);

    assertThatThrownBy(() -> retrospectiveService.saveDraft(ME, 101L, request))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.NOT_FOUND);
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

  // ---------- 홈·발자국 (지윤님 HomeService·FootprintService가 부름) ----------

  private static final java.time.LocalDateTime DONE_AT =
      java.time.LocalDateTime.of(2026, 11, 8, 21, 0);

  /** 내 회고 하나. done이면 완료 처리한다 */
  private static Retrospective retro(long id, long weekId, RetrospectiveType type, boolean done) {
    Retrospective r = Retrospective.start(ME, weekId, type);
    ReflectionTestUtils.setField(r, "id", id);
    if (done) {
      r.complete(DONE_AT);
    }
    return r;
  }

  private static CardSnapshot publishedCard(long cardId, Long weekId) {
    return new CardSnapshot(
        cardId, StoryCardType.SESSION, weekId, null, "카드", null, null, null, null, null, Set.of());
  }

  @Test
  @DisplayName("홈: 이번 주 회고가 없으면 NOT_STARTED·id null·이번 주 칸 표시. 스티커는 완료한 '세션' 회고의 주차만")
  void homeSummary_notStarted_stickersFromCompletedSessions() {
    when(retrospectiveRepository.findByUserIdAndWeekIdAndType(ME, WEEK, RetrospectiveType.SESSION))
        .thenReturn(Optional.empty());
    when(retrospectiveRepository.findAllByUserId(ME))
        .thenReturn(
            List.of(
                retro(81L, 8L, RetrospectiveType.SESSION, true),
                retro(91L, 9L, RetrospectiveType.SESSION, true),
                retro(92L, 9L, RetrospectiveType.ASSIGNMENT, true), // 과제 회고는 스티커가 아니다
                retro(71L, 7L, RetrospectiveType.SESSION, false))); // 진행 중도 아니다

    HomeRetroSummary summary = retrospectiveService.homeSummary(ME, WEEK);

    assertThat(summary.sessionRetrospectiveId()).isNull();
    assertThat(summary.state()).isEqualTo(RetrospectiveState.NOT_STARTED);
    assertThat(summary.stickerWeekIds()).containsExactlyInAnyOrder(8L, 9L);
    assertThat(summary.showThisWeekSlot()).isTrue();
  }

  @Test
  @DisplayName("홈: 이번 주 세션 회고를 쓰는 중이면 IN_PROGRESS와 그 id, 이번 주 칸 표시")
  void homeSummary_inProgress() {
    Retrospective thisWeek = retro(101L, WEEK, RetrospectiveType.SESSION, false);
    when(retrospectiveRepository.findByUserIdAndWeekIdAndType(ME, WEEK, RetrospectiveType.SESSION))
        .thenReturn(Optional.of(thisWeek));
    when(retrospectiveRepository.findAllByUserId(ME)).thenReturn(List.of(thisWeek));

    HomeRetroSummary summary = retrospectiveService.homeSummary(ME, WEEK);

    assertThat(summary.sessionRetrospectiveId()).isEqualTo(101L);
    assertThat(summary.state()).isEqualTo(RetrospectiveState.IN_PROGRESS);
    assertThat(summary.stickerWeekIds()).isEmpty();
    assertThat(summary.showThisWeekSlot()).isTrue();
  }

  @Test
  @DisplayName("홈: 이번 주를 완료했으면 COMPLETED이고 이번 주가 스티커에 들어가며, 빈 칸은 띄우지 않는다")
  void homeSummary_completed() {
    Retrospective thisWeek = retro(101L, WEEK, RetrospectiveType.SESSION, true);
    when(retrospectiveRepository.findByUserIdAndWeekIdAndType(ME, WEEK, RetrospectiveType.SESSION))
        .thenReturn(Optional.of(thisWeek));
    when(retrospectiveRepository.findAllByUserId(ME)).thenReturn(List.of(thisWeek));

    HomeRetroSummary summary = retrospectiveService.homeSummary(ME, WEEK);

    assertThat(summary.state()).isEqualTo(RetrospectiveState.COMPLETED);
    assertThat(summary.stickerWeekIds()).containsExactly(WEEK);
    assertThat(summary.showThisWeekSlot()).isFalse();
  }

  @Test
  @DisplayName("홈: 이번 주를 휴회했으면 RESTED이고 이번 주 칸을 띄우지 않는다")
  void homeSummary_rested() {
    Retrospective rested = Retrospective.startRested(ME, WEEK);
    ReflectionTestUtils.setField(rested, "id", 101L);
    when(retrospectiveRepository.findByUserIdAndWeekIdAndType(ME, WEEK, RetrospectiveType.SESSION))
        .thenReturn(Optional.of(rested));
    when(retrospectiveRepository.findAllByUserId(ME)).thenReturn(List.of(rested));

    HomeRetroSummary summary = retrospectiveService.homeSummary(ME, WEEK);

    assertThat(summary.state()).isEqualTo(RetrospectiveState.RESTED);
    assertThat(summary.stickerWeekIds()).isEmpty();
    assertThat(summary.showThisWeekSlot()).isFalse();
  }

  @Test
  @DisplayName("발자국: 회고를 주차별로 묶어 세션·과제 상태와 그 주 공개 카드 수를 주고, 회고 없는 주는 빠진다 (주차 ID 순)")
  void footprints_groupsByWeek() {
    when(retrospectiveRepository.findAllByUserId(ME))
        .thenReturn(
            List.of(
                retro(91L, 9L, RetrospectiveType.SESSION, true),
                retro(92L, 9L, RetrospectiveType.ASSIGNMENT, false),
                retro(81L, 8L, RetrospectiveType.SESSION, false)));
    when(storyCardService.findPublished(ME))
        .thenReturn(
            List.of(publishedCard(301L, 9L), publishedCard(302L, 9L), publishedCard(412L, null)));

    List<WeekRetroStatus> weeks = retrospectiveService.footprints(ME);

    assertThat(weeks)
        .containsExactly(
            new WeekRetroStatus(
                8L, RetrospectiveState.IN_PROGRESS, RetrospectiveState.NOT_STARTED, 0),
            new WeekRetroStatus(
                9L, RetrospectiveState.COMPLETED, RetrospectiveState.IN_PROGRESS, 2));
  }

  @Test
  @DisplayName("발자국: 회고가 하나도 없으면 빈 목록")
  void footprints_empty() {
    when(retrospectiveRepository.findAllByUserId(ME)).thenReturn(List.of());

    assertThat(retrospectiveService.footprints(ME)).isEmpty();
  }
}
