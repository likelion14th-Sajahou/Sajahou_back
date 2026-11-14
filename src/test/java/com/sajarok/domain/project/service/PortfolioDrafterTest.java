package com.sajarok.domain.project.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sajarok.domain.attachment.dto.AttachmentView;
import com.sajarok.domain.project.ai.FinishAttachments;
import com.sajarok.domain.project.ai.FinishPrompts;
import com.sajarok.domain.project.ai.PortfolioDraftResult;
import com.sajarok.domain.project.ai.PortfolioDraftResult.Section;
import com.sajarok.domain.project.entity.Project;
import com.sajarok.domain.question.dto.QuestionView;
import com.sajarok.domain.question.entity.QuestionStatus;
import com.sajarok.global.exception.BusinessException;
import com.sajarok.global.exception.ErrorCode;
import com.sajarok.infra.ai.AiClient;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** 33번 PortfolioDrafter 테스트. 실제 OpenAI 대신 가짜 AiClient를 쓴다. */
@ExtendWith(MockitoExtension.class)
class PortfolioDrafterTest {

  @Mock private AiClient aiClient;

  @InjectMocks private PortfolioDrafter drafter;

  private static final Project PROJECT = Project.builder().userId(1L).name("사자록").build();

  private Map<String, String> draft() {
    return drafter.draft(PROJECT, List.of(), List.of(), FinishAttachments.none());
  }

  @Test
  @DisplayName("AI가 순서를 섞어 줘도 6개 항목 key 순서로 정리하고, 빈 본문은 빈칸(null)으로 둔다")
  void draft_validResponse_orderedByKey() {
    List<Section> shuffled =
        List.of(
            new Section("learned", "배운 점"),
            new Section("role", "백엔드 API 담당"),
            new Section("contribution", null),
            new Section("problem", " "),
            new Section("result", null),
            new Section("artifact", null));
    when(aiClient.call(
            eq(FinishPrompts.PORTFOLIO_DRAFT_SYSTEM), anyString(), eq(PortfolioDraftResult.class)))
        .thenReturn(new PortfolioDraftResult(shuffled));

    Map<String, String> bodies = draft();

    assertThat(bodies.keySet())
        .containsExactly("role", "contribution", "problem", "result", "artifact", "learned");
    assertThat(bodies.get("role")).isEqualTo("백엔드 API 담당");
    assertThat(bodies.get("problem")).isNull();
  }

  @Test
  @DisplayName("AI가 항목을 빠뜨리면 AI_FAILED, AiClient의 AI_TIMEOUT은 그대로 올린다")
  void draft_invalidOrTimeout_throws() {
    when(aiClient.call(anyString(), anyString(), eq(PortfolioDraftResult.class)))
        .thenReturn(new PortfolioDraftResult(List.of(new Section("role", "백엔드"))))
        .thenThrow(new BusinessException(ErrorCode.AI_TIMEOUT));

    assertThatThrownBy(this::draft).extracting("errorCode").isEqualTo(ErrorCode.AI_FAILED);
    assertThatThrownBy(this::draft).extracting("errorCode").isEqualTo(ErrorCode.AI_TIMEOUT);
  }

  // ---- QA 6: 답한 항목이 비어 오면 한 번 다시 요청

  private static final FinishAttachments ARTIFACT =
      new FinishAttachments(
          List.of(new AttachmentView(610L, null, null, "발표.pdf", null, null, null, null, null)),
          List.of());

  private static QuestionView answered(String tag, String answer) {
    return new QuestionView(1L, 1, tag, null, null, "질문", answer, QuestionStatus.ANSWERED);
  }

  /** result·learned만 값을 주고 나머지는 null인 응답 */
  private static PortfolioDraftResult result(String result, String learned) {
    List<Section> sections = new ArrayList<>();
    for (String key : PortfolioDraftResult.KEYS) {
      String body = key.equals("result") ? result : key.equals("learned") ? learned : null;
      sections.add(new Section(key, body));
    }
    return new PortfolioDraftResult(sections);
  }

  private void firstCallReturns(PortfolioDraftResult r) {
    when(aiClient.call(anyString(), anyString(), eq(PortfolioDraftResult.class))).thenReturn(r);
  }

  private void retryReturns(PortfolioDraftResult r) {
    when(aiClient.call(
            anyString(), anyString(), eq(PortfolioDraftResult.class), any(Duration.class)))
        .thenReturn(r);
  }

  private Map<String, String> draft(List<QuestionView> answers, FinishAttachments attachments) {
    return drafter.draft(PROJECT, List.of(), answers, attachments);
  }

  @Test
  @DisplayName("QA 6: 답한 항목(최종 결과→result)이 비어 오면 [다시 채울 항목]을 붙여 1번 다시 요청하고, 채워진 값을 쓴다")
  void answeredButNull_retriesOnceAndUsesFilledValue() {
    firstCallReturns(result(null, "배운 점"));
    retryReturns(result("멘토님께 칭찬을 많이 받았습니다.", "다시 쓴 배운 점"));

    Map<String, String> bodies =
        draft(List.of(answered("최종 결과", "칭찬 많이 해 주심ㅋㅋ")), FinishAttachments.none());

    assertThat(bodies.get("result")).isEqualTo("멘토님께 칭찬을 많이 받았습니다.");
    assertThat(bodies.get("learned")).isEqualTo("배운 점");
    verify(aiClient)
        .call(
            anyString(),
            contains("[다시 채울 항목]\nresult(결과)"),
            eq(PortfolioDraftResult.class),
            any(Duration.class));
  }

  @Test
  @DisplayName("QA 6: 다시 요청해도 비어 있으면 null로 두고, AI 호출은 2번에서 멈춘다")
  void stillNull_keepsNullAndCallsTwiceOnly() {
    firstCallReturns(result(null, null));
    retryReturns(result(null, null));

    Map<String, String> bodies =
        draft(List.of(answered("근거와 숫자", "사용자 50명 넘음!!")), FinishAttachments.none());

    assertThat(bodies.get("result")).isNull();
    verify(aiClient, times(1)).call(anyString(), anyString(), eq(PortfolioDraftResult.class));
    verify(aiClient, times(1))
        .call(anyString(), anyString(), eq(PortfolioDraftResult.class), any(Duration.class));
  }

  @Test
  @DisplayName("QA 6: 산출물 요청에 \"아직 없어요\"(산출물 첨부 없음)로 답해 artifact가 비면 다시 요청하지 않는다")
  void artifactWithoutAttachment_noRetry() {
    firstCallReturns(result("결과", null));

    draft(List.of(answered("산출물 요청", "아직 없어요")), FinishAttachments.none());

    verify(aiClient, never())
        .call(anyString(), anyString(), eq(PortfolioDraftResult.class), any(Duration.class));
  }

  @Test
  @DisplayName("QA 6: 산출물 첨부가 있는데 artifact가 비어 오면 (답 글이 없어도) 다시 요청한다")
  void artifactWithAttachment_retries() {
    firstCallReturns(result("결과", null));
    retryReturns(result("결과", null));

    draft(List.of(answered("산출물 요청", null)), ARTIFACT);

    verify(aiClient)
        .call(
            anyString(),
            contains("artifact(산출물)"),
            eq(PortfolioDraftResult.class),
            any(Duration.class));
  }

  @Test
  @DisplayName("QA 6: 답한 항목이 다 채워져 있으면 다시 요청하지 않는다")
  void allAnsweredFilled_noRetry() {
    firstCallReturns(result("결과", "배운 점"));

    draft(List.of(answered("최종 결과", "수상함"), answered("배운 점", "명세 먼저")), FinishAttachments.none());

    verify(aiClient, never())
        .call(anyString(), anyString(), eq(PortfolioDraftResult.class), any(Duration.class));
  }

  @Test
  @DisplayName("QA 6: 첫 호출이 35초 걸려 남은 시간이 20초보다 적으면 다시 요청하지 않는다 (전체 50초 안)")
  void notEnoughTime_noRetry() {
    Clock clock = mock(Clock.class);
    Instant t0 = Instant.parse("2026-11-15T00:00:00Z");
    when(clock.instant()).thenReturn(t0, t0.plusSeconds(35));
    drafter.clock = clock;
    firstCallReturns(result(null, null));

    Map<String, String> bodies = draft(List.of(answered("최종 결과", "수상함")), FinishAttachments.none());

    assertThat(bodies.get("result")).isNull();
    verify(aiClient, never())
        .call(anyString(), anyString(), eq(PortfolioDraftResult.class), any(Duration.class));
  }

  @Test
  @DisplayName("QA 6: 다시 요청할 때 제한 시간은 50초에서 첫 호출 시간을 뺀 값이다")
  void retryTimeout_isRemainingBudget() {
    Clock clock = mock(Clock.class);
    Instant t0 = Instant.parse("2026-11-15T00:00:00Z");
    when(clock.instant()).thenReturn(t0, t0.plusSeconds(12));
    drafter.clock = clock;
    firstCallReturns(result(null, null));
    retryReturns(result("수상했습니다.", null));

    draft(List.of(answered("최종 결과", "수상함")), FinishAttachments.none());

    verify(aiClient)
        .call(anyString(), anyString(), eq(PortfolioDraftResult.class), eq(Duration.ofSeconds(38)));
  }

  @Test
  @DisplayName("QA 6: 다시 요청이 시간 초과로 실패해도 요청 전체를 실패시키지 않고 첫 결과를 쓴다")
  void retryFails_usesFirstResult() {
    firstCallReturns(result(null, "배운 점"));
    when(aiClient.call(
            anyString(), anyString(), eq(PortfolioDraftResult.class), any(Duration.class)))
        .thenThrow(new BusinessException(ErrorCode.AI_TIMEOUT));

    Map<String, String> bodies = draft(List.of(answered("최종 결과", "수상함")), FinishAttachments.none());

    assertThat(bodies.get("result")).isNull();
    assertThat(bodies.get("learned")).isEqualTo("배운 점");
  }
}
