package com.sajarok.domain.retrospective.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sajarok.domain.retrospective.dto.RetrospectiveRestResponse;
import com.sajarok.domain.retrospective.dto.RetrospectiveStartRequest;
import com.sajarok.domain.retrospective.dto.RetrospectiveState;
import com.sajarok.domain.retrospective.entity.AssignmentMode;
import com.sajarok.domain.retrospective.entity.Retrospective;
import com.sajarok.domain.retrospective.entity.RetrospectiveType;
import com.sajarok.domain.retrospective.repository.RetrospectiveRepository;
import com.sajarok.domain.retrospective.service.RetrospectiveService.StartResult;
import com.sajarok.global.exception.BusinessException;
import com.sajarok.global.exception.ErrorCode;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 서비스 테스트: 12번 회고 시작(이미 있으면 그대로, 휴회, 과제 회고 조건)과 15번 휴회 표시·해제 규칙을 확인한다.
 *
 * <p>DB 없이 레포지토리를 가짜(@Mock)로 바꾼다. 주차 확인은 주차 모듈이 생기면 더한다.
 */
@ExtendWith(MockitoExtension.class)
class RetrospectiveServiceTest {

  private static final Long ME = 1L;
  private static final Long WEEK = 10L;

  @Mock private RetrospectiveRepository retrospectiveRepository;

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
}
