package com.sajarok.domain.retrospective.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sajarok.domain.attachment.dto.AttachmentView;
import com.sajarok.domain.attachment.entity.AttachmentCategory;
import com.sajarok.domain.attachment.entity.AttachmentType;
import com.sajarok.domain.attachment.entity.ExtractStatus;
import com.sajarok.domain.attachment.entity.LinkType;
import com.sajarok.domain.question.dto.QuestionView;
import com.sajarok.domain.question.entity.QuestionStatus;
import com.sajarok.domain.retrospective.dto.RetroQuestionMode;
import com.sajarok.domain.retrospective.dto.RetrospectiveCompleteResponse;
import com.sajarok.domain.retrospective.dto.RetrospectiveCompleteResponse.AssignmentPending;
import com.sajarok.domain.retrospective.dto.RetrospectiveCurrentResponse;
import com.sajarok.domain.retrospective.dto.RetrospectiveCurrentResponse.OpenAssignment;
import com.sajarok.domain.retrospective.dto.RetrospectiveCurrentResponse.SessionView;
import com.sajarok.domain.retrospective.dto.RetrospectiveCurrentResponse.WeekView;
import com.sajarok.domain.retrospective.dto.RetrospectiveDetailResponse;
import com.sajarok.domain.retrospective.dto.RetrospectiveDetailResponse.Materials;
import com.sajarok.domain.retrospective.dto.RetrospectiveDraftRequest;
import com.sajarok.domain.retrospective.dto.RetrospectiveProgressResponse;
import com.sajarok.domain.retrospective.dto.RetrospectiveQuestionsResponse;
import com.sajarok.domain.retrospective.dto.RetrospectiveRestResponse;
import com.sajarok.domain.retrospective.dto.RetrospectiveReviewResponse;
import com.sajarok.domain.retrospective.dto.RetrospectiveStartRequest;
import com.sajarok.domain.retrospective.dto.RetrospectiveStartResponse;
import com.sajarok.domain.retrospective.dto.RetrospectiveState;
import com.sajarok.domain.retrospective.entity.AssignmentMode;
import com.sajarok.domain.retrospective.entity.RetrospectiveType;
import com.sajarok.domain.retrospective.service.RetrospectiveAiService;
import com.sajarok.domain.retrospective.service.RetrospectiveAiService.QuestionsResult;
import com.sajarok.domain.retrospective.service.RetrospectiveService;
import com.sajarok.domain.retrospective.service.RetrospectiveService.StartResult;
import com.sajarok.domain.storycard.dto.StoryCardDetailResponse;
import com.sajarok.domain.storycard.entity.StoryCardStatus;
import com.sajarok.domain.storycard.entity.StoryCardType;
import com.sajarok.global.auth.CurrentUserProvider;
import com.sajarok.global.exception.BusinessException;
import com.sajarok.global.exception.ErrorCode;
import com.sajarok.global.time.KstTime;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 컨트롤러 테스트: 회고 API의 상태 코드와 응답 모양이 API 명세와 같은지 확인한다.
 *
 * <p>서비스는 가짜(@MockitoBean)로 바꾸고, 보안 필터는 테스트에서만 끈다 (addFilters = false).
 */
@WebMvcTest(RetrospectiveController.class)
@AutoConfigureMockMvc(addFilters = false)
class RetrospectiveControllerTest {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private RetrospectiveService retrospectiveService;
  @MockitoBean private RetrospectiveAiService retrospectiveAiService;
  @MockitoBean private CurrentUserProvider currentUserProvider;

  @BeforeEach
  void setUp() {
    when(currentUserProvider.getUserId()).thenReturn(1L);
  }

  private static RetrospectiveStartResponse started() {
    return new RetrospectiveStartResponse(
        101L, RetrospectiveType.SESSION, null, RetrospectiveState.IN_PROGRESS, 2);
  }

  @Test
  @DisplayName("12번: 새로 만들면 201과 명세 필드(id, type, weekNumber, state, currentStep)를 준다")
  void start_new_returns201() throws Exception {
    when(retrospectiveService.start(eq(1L), any(RetrospectiveStartRequest.class)))
        .thenReturn(new StartResult(started(), true));

    mockMvc
        .perform(
            post("/api/v1/retrospectives")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"weekId\": 10, \"type\": \"SESSION\", \"rested\": false}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.id").value(101))
        .andExpect(jsonPath("$.data.type").value("SESSION"))
        .andExpect(jsonPath("$.data.weekNumber").value(nullValue()))
        .andExpect(jsonPath("$.data.state").value("IN_PROGRESS"))
        .andExpect(jsonPath("$.data.currentStep").value(2));
  }

  @Test
  @DisplayName("12번: 이미 있으면 200과 기존 회고를 준다")
  void start_existing_returns200() throws Exception {
    when(retrospectiveService.start(eq(1L), any(RetrospectiveStartRequest.class)))
        .thenReturn(new StartResult(started(), false));

    mockMvc
        .perform(
            post("/api/v1/retrospectives")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"weekId\": 10, \"type\": \"SESSION\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.id").value(101));
  }

  @Test
  @DisplayName("12번: weekId가 없으면 400 VALIDATION_ERROR와 weekId 칸의 이유를 주고, 서비스는 부르지 않는다")
  void start_missingWeek_returns400() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/retrospectives")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"type\": \"SESSION\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
        .andExpect(jsonPath("$.error.fields[0].field").value("weekId"));
    verifyNoInteractions(retrospectiveService);
  }

  @Test
  @DisplayName("12번: 과제 회고를 만들 수 없는 상태면 409 INVALID_STATE를 준다")
  void start_invalidState_returns409() throws Exception {
    when(retrospectiveService.start(eq(1L), any(RetrospectiveStartRequest.class)))
        .thenThrow(new BusinessException(ErrorCode.INVALID_STATE));

    mockMvc
        .perform(
            post("/api/v1/retrospectives")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"weekId\": 10, \"type\": \"ASSIGNMENT\"}"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.error.code").value("INVALID_STATE"));
  }

  @Test
  @DisplayName("15번: 휴회로 표시하면 200과 id·state를 주고, 로그인한 회원 ID로 서비스를 부른다")
  void rest_returns200() throws Exception {
    when(retrospectiveService.changeRested(1L, 101L, true))
        .thenReturn(new RetrospectiveRestResponse(101L, RetrospectiveState.RESTED));

    mockMvc
        .perform(
            patch("/api/v1/retrospectives/101/rest")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"rested\": true}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.id").value(101))
        .andExpect(jsonPath("$.data.state").value("RESTED"));
  }

  @Test
  @DisplayName("15번: rested가 없으면 400 VALIDATION_ERROR와 rested 칸의 이유를 주고, 서비스는 부르지 않는다")
  void rest_missingRested_returns400() throws Exception {
    mockMvc
        .perform(
            patch("/api/v1/retrospectives/101/rest")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
        .andExpect(jsonPath("$.error.fields[0].field").value("rested"));
    verifyNoInteractions(retrospectiveService);
  }

  @Test
  @DisplayName("15번: 과제 회고나 완료한 회고면 409 INVALID_STATE를 준다")
  void rest_invalidState_returns409() throws Exception {
    when(retrospectiveService.changeRested(1L, 205L, true))
        .thenThrow(new BusinessException(ErrorCode.INVALID_STATE));

    mockMvc
        .perform(
            patch("/api/v1/retrospectives/205/rest")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"rested\": true}"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.error.code").value("INVALID_STATE"));
  }

  @Test
  @DisplayName("16번: 처음부터 하면 200과 id·state·currentStep(2)을 준다")
  void reset_returns200() throws Exception {
    when(retrospectiveService.reset(1L, 101L))
        .thenReturn(
            new RetrospectiveProgressResponse(101L, RetrospectiveState.IN_PROGRESS, 2, null));

    mockMvc
        .perform(post("/api/v1/retrospectives/101/reset"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.id").value(101))
        .andExpect(jsonPath("$.data.state").value("IN_PROGRESS"))
        .andExpect(jsonPath("$.data.currentStep").value(2));
  }

  @Test
  @DisplayName("16번: 완료한 회고면 409 INVALID_STATE를 준다")
  void reset_completed_returns409() throws Exception {
    when(retrospectiveService.reset(1L, 101L))
        .thenThrow(new BusinessException(ErrorCode.INVALID_STATE));

    mockMvc
        .perform(post("/api/v1/retrospectives/101/reset"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.error.code").value("INVALID_STATE"));
  }

  @Test
  @DisplayName(
      "13번: 상세는 200과 명세 모양(isRested, materials, attachments, questions, week·card null)을 준다")
  void detail_returns200() throws Exception {
    when(retrospectiveService.getDetail(1L, 101L))
        .thenReturn(
            new RetrospectiveDetailResponse(
                101L,
                RetrospectiveType.SESSION,
                RetrospectiveState.IN_PROGRESS,
                false,
                3,
                KstTime.toOffset(LocalDateTime.of(2026, 11, 5, 22, 40)),
                null,
                null,
                new Materials(true, "실습 내용", AssignmentMode.RECORD, "과제 내용", null, "메모"),
                List.of(
                    new AttachmentView(
                        501L,
                        AttachmentType.LINK,
                        AttachmentCategory.ASSIGNMENT,
                        null,
                        "https://github.com/me/assignment",
                        LinkType.GITHUB,
                        null,
                        null,
                        ExtractStatus.DONE)),
                List.of(
                    new QuestionView(
                        901L,
                        1,
                        "근거와 숫자",
                        null,
                        null,
                        "쿼리 수가 실제로 얼마나 줄었나요?",
                        null,
                        QuestionStatus.PENDING)),
                null));

    mockMvc
        .perform(get("/api/v1/retrospectives/101"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.id").value(101))
        .andExpect(jsonPath("$.data.state").value("IN_PROGRESS"))
        .andExpect(jsonPath("$.data.isRested").value(false))
        .andExpect(jsonPath("$.data.currentStep").value(3))
        .andExpect(jsonPath("$.data.lastSavedAt").value("2026-11-05T22:40:00+09:00"))
        .andExpect(jsonPath("$.data.week").value(nullValue()))
        .andExpect(jsonPath("$.data.materials.assignmentMode").value("RECORD"))
        .andExpect(jsonPath("$.data.materials.memo").value("메모"))
        .andExpect(jsonPath("$.data.attachments[0].attachmentId").value(501))
        .andExpect(jsonPath("$.data.attachments[0].type").value("LINK"))
        .andExpect(jsonPath("$.data.attachments[0].linkType").value("GITHUB"))
        .andExpect(jsonPath("$.data.attachments[0].extractStatus").value("DONE"))
        .andExpect(jsonPath("$.data.questions[0].questionId").value(901))
        .andExpect(jsonPath("$.data.questions[0].status").value("PENDING"))
        .andExpect(jsonPath("$.data.card").value(nullValue()));
  }

  @Test
  @DisplayName("13번: 없거나 남의 회고면 404 NOT_FOUND를 준다")
  void detail_notMine_returns404() throws Exception {
    when(retrospectiveService.getDetail(1L, 999L))
        .thenThrow(new BusinessException(ErrorCode.NOT_FOUND));

    mockMvc
        .perform(get("/api/v1/retrospectives/999"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
  }

  @Test
  @DisplayName("14번: JSON에 null로 보낸 칸은 '보냄'으로, 아예 뺀 칸은 '안 보냄'으로 서비스에 넘어가고, 저장 시각은 +09:00으로 준다")
  void draft_distinguishesNullAndMissing() throws Exception {
    when(retrospectiveService.saveDraft(
            org.mockito.ArgumentMatchers.eq(1L),
            org.mockito.ArgumentMatchers.eq(101L),
            any(RetrospectiveDraftRequest.class)))
        .thenReturn(
            new RetrospectiveProgressResponse(
                101L,
                RetrospectiveState.IN_PROGRESS,
                3,
                KstTime.toOffset(LocalDateTime.of(2026, 11, 12, 0, 30))));

    mockMvc
        .perform(
            patch("/api/v1/retrospectives/101")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"currentStep\": 3, \"memo\": null}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.currentStep").value(3))
        .andExpect(jsonPath("$.data.lastSavedAt").value("2026-11-12T00:30:00+09:00"));

    ArgumentCaptor<RetrospectiveDraftRequest> captor =
        ArgumentCaptor.forClass(RetrospectiveDraftRequest.class);
    verify(retrospectiveService)
        .saveDraft(
            org.mockito.ArgumentMatchers.eq(1L),
            org.mockito.ArgumentMatchers.eq(101L),
            captor.capture());
    RetrospectiveDraftRequest sent = captor.getValue();
    assertThat(sent.has("memo")).isTrue();
    assertThat(sent.getMemo()).isNull();
    assertThat(sent.has("practiceContent")).isFalse();
    assertThat(sent.has("attachmentIds")).isFalse();
    assertThat(sent.getCurrentStep()).isEqualTo(3);
  }

  @Test
  @DisplayName("14번: attachmentIds를 보내면 '보냄'으로 표시되고 목록 그대로 서비스에 넘어간다")
  void draft_passesAttachmentIds() throws Exception {
    when(retrospectiveService.saveDraft(
            org.mockito.ArgumentMatchers.eq(1L),
            org.mockito.ArgumentMatchers.eq(101L),
            any(RetrospectiveDraftRequest.class)))
        .thenReturn(
            new RetrospectiveProgressResponse(101L, RetrospectiveState.IN_PROGRESS, 2, null));

    mockMvc
        .perform(
            patch("/api/v1/retrospectives/101")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"attachmentIds\": [501, 502]}"))
        .andExpect(status().isOk());

    ArgumentCaptor<RetrospectiveDraftRequest> captor =
        ArgumentCaptor.forClass(RetrospectiveDraftRequest.class);
    verify(retrospectiveService)
        .saveDraft(
            org.mockito.ArgumentMatchers.eq(1L),
            org.mockito.ArgumentMatchers.eq(101L),
            captor.capture());
    assertThat(captor.getValue().has("attachmentIds")).isTrue();
    assertThat(captor.getValue().getAttachmentIds()).containsExactly(501L, 502L);
    assertThat(captor.getValue().has("memo")).isFalse();
  }

  @Test
  @DisplayName("14번: 단계가 2~4가 아니면 400 VALIDATION_ERROR와 currentStep 칸의 이유를 준다")
  void draft_invalidStep_returns400() throws Exception {
    mockMvc
        .perform(
            patch("/api/v1/retrospectives/101")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"currentStep\": 5}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
        .andExpect(jsonPath("$.error.fields[0].field").value("currentStep"));
    verifyNoInteractions(retrospectiveService);
  }

  @Test
  @DisplayName("14번: 과제 작성 방식이 정해진 값이 아니면 400 VALIDATION_ERROR를 준다")
  void draft_unknownAssignmentMode_returns400() throws Exception {
    mockMvc
        .perform(
            patch("/api/v1/retrospectives/101")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"assignmentMode\": \"FOO\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    verifyNoInteractions(retrospectiveService);
  }

  @Test
  @DisplayName("20번: 저장하면 200과 state·cardId·isLate·assignmentPending을 준다")
  void complete_returns200() throws Exception {
    when(retrospectiveService.complete(1L, 101L))
        .thenReturn(
            new RetrospectiveCompleteResponse(
                RetrospectiveState.COMPLETED,
                301L,
                null,
                false,
                new AssignmentPending(null, null)));

    mockMvc
        .perform(post("/api/v1/retrospectives/101/complete"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.state").value("COMPLETED"))
        .andExpect(jsonPath("$.data.cardId").value(301))
        .andExpect(jsonPath("$.data.isLate").value(false))
        .andExpect(jsonPath("$.data.weekNumber").value(nullValue()))
        .andExpect(jsonPath("$.data.assignmentPending.deadline").value(nullValue()));
  }

  @Test
  @DisplayName("20번: 역량 태그를 1~3개 고르지 않았으면 400 COMPETENCY_LIMIT를 준다")
  void complete_competencyLimit_returns400() throws Exception {
    when(retrospectiveService.complete(1L, 101L))
        .thenThrow(new BusinessException(ErrorCode.COMPETENCY_LIMIT));

    mockMvc
        .perform(post("/api/v1/retrospectives/101/complete"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("COMPETENCY_LIMIT"));
  }

  @Test
  @DisplayName("20번: 카드 초안이 없으면 409 INVALID_STATE를 준다")
  void complete_noCard_returns409() throws Exception {
    when(retrospectiveService.complete(1L, 101L))
        .thenThrow(new BusinessException(ErrorCode.INVALID_STATE));

    mockMvc
        .perform(post("/api/v1/retrospectives/101/complete"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.error.code").value("INVALID_STATE"));
  }

  private static QuestionView pendingQuestion() {
    return new QuestionView(
        901L,
        1,
        "근거와 숫자",
        "N+1 문제를 fetch join으로 해결",
        null,
        "쿼리 수가 실제로 얼마나 줄었나요?",
        null,
        QuestionStatus.PENDING);
  }

  @Test
  @DisplayName("17번: 질문을 새로 만들면 201과 명세 모양(insufficientMaterial, questions)을 준다")
  void questions_created_returns201() throws Exception {
    when(retrospectiveAiService.createQuestions(1L, 101L, RetroQuestionMode.AUTO))
        .thenReturn(
            new QuestionsResult(
                true, RetrospectiveQuestionsResponse.of(List.of(pendingQuestion()))));

    mockMvc
        .perform(
            post("/api/v1/retrospectives/101/questions")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"mode\": \"AUTO\"}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.insufficientMaterial").value(false))
        .andExpect(jsonPath("$.data.questions[0].questionId").value(901))
        .andExpect(jsonPath("$.data.questions[0].seq").value(1))
        .andExpect(jsonPath("$.data.questions[0].tag").value("근거와 숫자"))
        .andExpect(jsonPath("$.data.questions[0].quote").value("N+1 문제를 fetch join으로 해결"))
        .andExpect(jsonPath("$.data.questions[0].sourceAttachmentId").value(nullValue()))
        .andExpect(jsonPath("$.data.questions[0].answer").value(nullValue()))
        .andExpect(jsonPath("$.data.questions[0].status").value("PENDING"));
  }

  @Test
  @DisplayName("17번: 본문 없이 보내면 mode null로 서비스를 부르고(서비스가 AUTO로 처리), 저장된 질문이면 200을 준다")
  void questions_noBody_returns200() throws Exception {
    when(retrospectiveAiService.createQuestions(1L, 101L, null))
        .thenReturn(
            new QuestionsResult(
                false, RetrospectiveQuestionsResponse.of(List.of(pendingQuestion()))));

    mockMvc
        .perform(post("/api/v1/retrospectives/101/questions"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.questions.length()").value(1));
    verify(retrospectiveAiService).createQuestions(1L, 101L, null);
  }

  @Test
  @DisplayName("17번: mode를 REGENERATE로 보내면 그대로 서비스에 넘긴다")
  void questions_regenerate_passesMode() throws Exception {
    when(retrospectiveAiService.createQuestions(1L, 101L, RetroQuestionMode.REGENERATE))
        .thenReturn(
            new QuestionsResult(
                true, RetrospectiveQuestionsResponse.of(List.of(pendingQuestion()))));

    mockMvc
        .perform(
            post("/api/v1/retrospectives/101/questions")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"mode\": \"REGENERATE\"}"))
        .andExpect(status().isCreated());
    verify(retrospectiveAiService).createQuestions(1L, 101L, RetroQuestionMode.REGENERATE);
  }

  @Test
  @DisplayName("17번: 자료가 부족하면 200과 insufficientMaterial=true, 빈 questions를 준다")
  void questions_insufficient_returns200() throws Exception {
    when(retrospectiveAiService.createQuestions(1L, 101L, null))
        .thenReturn(new QuestionsResult(false, RetrospectiveQuestionsResponse.insufficient()));

    mockMvc
        .perform(post("/api/v1/retrospectives/101/questions"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.insufficientMaterial").value(true))
        .andExpect(jsonPath("$.data.questions.length()").value(0));
  }

  @Test
  @DisplayName("17번: 자료가 바뀌었으면 409 MATERIAL_CHANGED와 error.detail.answeredCount를 준다")
  void questions_materialChanged_returns409WithDetail() throws Exception {
    when(retrospectiveAiService.createQuestions(1L, 101L, null))
        .thenThrow(
            new BusinessException(
                ErrorCode.MATERIAL_CHANGED,
                "자료가 바뀌었어요. 질문을 새로 만들면 지금까지 쓴 답 3개가 사라져요.",
                Map.of("answeredCount", 3L)));

    mockMvc
        .perform(post("/api/v1/retrospectives/101/questions"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.error.code").value("MATERIAL_CHANGED"))
        .andExpect(jsonPath("$.error.message").value("자료가 바뀌었어요. 질문을 새로 만들면 지금까지 쓴 답 3개가 사라져요."))
        .andExpect(jsonPath("$.error.detail.answeredCount").value(3));
  }

  @Test
  @DisplayName("17번: mode가 정해진 값이 아니면 400 VALIDATION_ERROR이고, 서비스는 부르지 않는다")
  void questions_unknownMode_returns400() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/retrospectives/101/questions")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"mode\": \"FOO\"}"))
        .andExpect(status().isBadRequest());
    verifyNoInteractions(retrospectiveAiService);
  }

  @Test
  @DisplayName("19번: 초안을 만들면 200과 { card } 모양을 주고, evidence는 문자열이 아니라 JSON 객체로 나간다")
  void review_returns200WithCard() throws Exception {
    when(retrospectiveAiService.review(1L, 101L))
        .thenReturn(
            new RetrospectiveReviewResponse(
                new StoryCardDetailResponse(
                    301L,
                    StoryCardType.SESSION,
                    StoryCardStatus.DRAFT,
                    null,
                    null,
                    null,
                    "N+1 문제를 fetch join으로 해결",
                    "게시글 목록 API에서 N+1 문제가 생겼다.",
                    "fetch join으로 바꿨다.",
                    null,
                    "쿼리 21개 → 2개",
                    null,
                    List.of("reason", "learned"),
                    List.of(),
                    "# 세션 회고\n\n## 실습\n...",
                    "{\"situation\":[{\"attachmentId\":501,\"quote\":\"N+1 문제\"}]}",
                    List.of(),
                    null)));

    mockMvc
        .perform(post("/api/v1/retrospectives/101/review"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.card.cardId").value(301))
        .andExpect(jsonPath("$.data.card.type").value("SESSION"))
        .andExpect(jsonPath("$.data.card.status").value("DRAFT"))
        .andExpect(jsonPath("$.data.card.reason").value(nullValue()))
        .andExpect(jsonPath("$.data.card.blanks[0]").value("reason"))
        .andExpect(jsonPath("$.data.card.competencies.length()").value(0))
        .andExpect(jsonPath("$.data.card.evidence.situation[0].attachmentId").value(501))
        .andExpect(jsonPath("$.data.card.evidence.situation[0].quote").value("N+1 문제"));
  }

  @Test
  @DisplayName("19번: 자료가 하나도 없으면 400 EMPTY_MATERIAL을 준다")
  void review_emptyMaterial_returns400() throws Exception {
    when(retrospectiveAiService.review(1L, 101L))
        .thenThrow(new BusinessException(ErrorCode.EMPTY_MATERIAL));

    mockMvc
        .perform(post("/api/v1/retrospectives/101/review"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("EMPTY_MATERIAL"));
  }

  @Test
  @DisplayName(
      "11번: 이번 주 회고는 200과 명세 모양(week, session, openAssignments)을 주고, /current가 /{id}보다 먼저 잡힌다")
  void current_returns200() throws Exception {
    when(retrospectiveService.getCurrent(1L))
        .thenReturn(
            new RetrospectiveCurrentResponse(
                new WeekView(
                    10L,
                    10,
                    "아이디어톤 준비",
                    KstTime.toOffset(LocalDateTime.of(2026, 11, 8, 23, 59)),
                    KstTime.toOffset(LocalDateTime.of(2026, 11, 10, 23, 59))),
                new SessionView(
                    101L,
                    RetrospectiveState.IN_PROGRESS,
                    3,
                    AssignmentMode.LATER,
                    KstTime.toOffset(LocalDateTime.of(2026, 11, 5, 22, 40))),
                List.of(
                    new OpenAssignment(
                        null,
                        10,
                        RetrospectiveState.NOT_STARTED,
                        null,
                        null,
                        KstTime.toOffset(LocalDateTime.of(2026, 11, 10, 23, 59))))));

    mockMvc
        .perform(get("/api/v1/retrospectives/current"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.week.weekNumber").value(10))
        .andExpect(jsonPath("$.data.week.sessionDeadline").value("2026-11-08T23:59:00+09:00"))
        .andExpect(jsonPath("$.data.session.id").value(101))
        .andExpect(jsonPath("$.data.session.state").value("IN_PROGRESS"))
        .andExpect(jsonPath("$.data.session.assignmentMode").value("LATER"))
        .andExpect(jsonPath("$.data.openAssignments[0].id").value(nullValue()))
        .andExpect(jsonPath("$.data.openAssignments[0].state").value("NOT_STARTED"))
        .andExpect(
            jsonPath("$.data.openAssignments[0].deadline").value("2026-11-10T23:59:00+09:00"));
    verify(retrospectiveService, org.mockito.Mockito.never()).getDetail(any(), any());
  }
}
