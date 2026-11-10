package com.sajarok.domain.project.controller;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sajarok.domain.project.dto.FinishQuestionsResponse;
import com.sajarok.domain.project.dto.PortfolioDraftResponse;
import com.sajarok.domain.project.dto.PortfolioSaveResponse;
import com.sajarok.domain.project.dto.ProjectDetailResponse.Portfolio;
import com.sajarok.domain.project.entity.ProjectStatus;
import com.sajarok.domain.project.service.ProjectFinishService;
import com.sajarok.domain.project.service.ProjectFinishService.FinishQuestions;
import com.sajarok.domain.question.entity.QuestionStatus;
import com.sajarok.global.auth.CurrentUserProvider;
import com.sajarok.global.exception.BusinessException;
import com.sajarok.global.exception.ErrorCode;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 컨트롤러 테스트: 32·33·34번 응답 JSON이 API 명세(필드 이름, 날짜 형식, 상태 코드, 에러 형식)와 같은지 확인한다.
 *
 * <p>보안 설정은 인증 담당 몫이라 테스트에서만 보안 필터를 끈다 (addFilters = false).
 */
@WebMvcTest(ProjectFinishController.class)
@AutoConfigureMockMvc(addFilters = false)
class ProjectFinishControllerTest {

  private static final String URL = "/api/v1/projects/3/finish/complete";

  @Autowired private MockMvc mockMvc;

  @MockitoBean private ProjectFinishService projectFinishService;
  @MockitoBean private CurrentUserProvider currentUserProvider;

  @BeforeEach
  void setUp() {
    when(currentUserProvider.getUserId()).thenReturn(1L);
  }

  @Test
  @DisplayName("34번: 200과 명세 필드(projectStatus, isFirstCompletion, portfolio.savedAt +09:00)를 준다")
  void complete_returns200WithSpecFields() throws Exception {
    when(projectFinishService.savePortfolio(1L, 3L, "# 사자록"))
        .thenReturn(
            new PortfolioSaveResponse(
                ProjectStatus.DONE,
                true,
                new Portfolio("# 사자록", OffsetDateTime.parse("2026-11-18T21:00:00+09:00"))));

    mockMvc
        .perform(
            post(URL).contentType(MediaType.APPLICATION_JSON).content("{\"markdown\": \"# 사자록\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.projectStatus").value("DONE"))
        .andExpect(jsonPath("$.data.isFirstCompletion").value(true))
        .andExpect(jsonPath("$.data.portfolio.markdown").value("# 사자록"))
        .andExpect(jsonPath("$.data.portfolio.savedAt").value("2026-11-18T21:00:00+09:00"));
  }

  @Test
  @DisplayName("34번: markdown이 비면 400 VALIDATION_ERROR와 markdown 칸의 이유(fields)를 준다")
  void complete_blankMarkdown_returns400WithFields() throws Exception {
    mockMvc
        .perform(post(URL).contentType(MediaType.APPLICATION_JSON).content("{\"markdown\": \" \"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
        .andExpect(jsonPath("$.error.fields[0].field").value("markdown"));
    verifyNoInteractions(projectFinishService);
  }

  @Test
  @DisplayName("34번: 남의 프로젝트거나 없으면 404 NOT_FOUND")
  void complete_notMine_returns404() throws Exception {
    when(projectFinishService.savePortfolio(anyLong(), anyLong(), anyString()))
        .thenThrow(new BusinessException(ErrorCode.NOT_FOUND));

    mockMvc
        .perform(
            post(URL).contentType(MediaType.APPLICATION_JSON).content("{\"markdown\": \"# 사자록\"}"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
  }

  private static final String QUESTIONS_URL = "/api/v1/projects/3/finish/questions";

  private static FinishQuestions questions(boolean created, String answer, QuestionStatus status) {
    return new FinishQuestions(
        created,
        new FinishQuestionsResponse(
            List.of(
                new FinishQuestionsResponse.Item(
                    980L,
                    4,
                    "산출물 요청",
                    "최종 산출물이 아직 첨부되지 않았어요",
                    "포트폴리오에 넣을 산출물을 첨부해 주세요. 최종 발표 자료, 시연 링크, 피그마가 있으면 좋아요.",
                    answer,
                    status))));
  }

  @Test
  @DisplayName("32번: 새로 만들면 201과 명세 필드(questionId·seq·tag·quote·content·answer null·status)를 준다")
  void createQuestions_new_returns201WithSpecFields() throws Exception {
    when(projectFinishService.createQuestions(1L, 3L, List.of(71L, 74L, 77L)))
        .thenReturn(questions(true, null, QuestionStatus.PENDING));

    mockMvc
        .perform(
            post(QUESTIONS_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"recordIds\": [71, 74, 77]}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.questions[0].questionId").value(980))
        .andExpect(jsonPath("$.data.questions[0].seq").value(4))
        .andExpect(jsonPath("$.data.questions[0].tag").value("산출물 요청"))
        .andExpect(jsonPath("$.data.questions[0].quote").value("최종 산출물이 아직 첨부되지 않았어요"))
        .andExpect(
            jsonPath("$.data.questions[0].content")
                .value("포트폴리오에 넣을 산출물을 첨부해 주세요. 최종 발표 자료, 시연 링크, 피그마가 있으면 좋아요."))
        .andExpect(jsonPath("$.data.questions[0].answer").value(nullValue()))
        .andExpect(jsonPath("$.data.questions[0].status").value("PENDING"))
        .andExpect(jsonPath("$.data.questions[0].sourceAttachmentId").doesNotExist());
  }

  @Test
  @DisplayName("32번: 진행 중인 질문이 있으면 200과 저장된 답을 그대로 준다")
  void createQuestions_existing_returns200WithAnswer() throws Exception {
    when(projectFinishService.createQuestions(1L, 3L, List.of(71L)))
        .thenReturn(questions(false, "발표 자료 링크예요", QuestionStatus.ANSWERED));

    mockMvc
        .perform(
            post(QUESTIONS_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"recordIds\": [71]}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.questions[0].answer").value("발표 자료 링크예요"))
        .andExpect(jsonPath("$.data.questions[0].status").value("ANSWERED"));
  }

  @Test
  @DisplayName(
      "32번: recordIds가 비었거나 51개 이상이면 400 VALIDATION_ERROR와 recordIds 칸의 이유를 주고, 서비스를 부르지 않는다")
  void createQuestions_invalidRecordIds_returns400() throws Exception {
    String fiftyOne =
        IntStream.rangeClosed(1, 51).mapToObj(String::valueOf).collect(Collectors.joining(","));
    for (String body : List.of("{\"recordIds\": []}", "{\"recordIds\": [" + fiftyOne + "]}")) {
      mockMvc
          .perform(post(QUESTIONS_URL).contentType(MediaType.APPLICATION_JSON).content(body))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
          .andExpect(jsonPath("$.error.fields[0].field").value("recordIds"))
          .andExpect(jsonPath("$.error.fields[0].reason").exists());
    }
    verifyNoInteractions(projectFinishService);
  }

  @Test
  @DisplayName("32번: 남의 프로젝트·기록이면 404 NOT_FOUND, AI 실패는 502 AI_FAILED, 시간 초과는 504 AI_TIMEOUT")
  void createQuestions_errors_mapToSpecStatuses() throws Exception {
    when(projectFinishService.createQuestions(anyLong(), anyLong(), anyList()))
        .thenThrow(new BusinessException(ErrorCode.NOT_FOUND))
        .thenThrow(new BusinessException(ErrorCode.AI_FAILED))
        .thenThrow(new BusinessException(ErrorCode.AI_TIMEOUT));
    String body = "{\"recordIds\": [71]}";

    mockMvc
        .perform(post(QUESTIONS_URL).contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
    mockMvc
        .perform(post(QUESTIONS_URL).contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isBadGateway())
        .andExpect(jsonPath("$.error.code").value("AI_FAILED"));
    mockMvc
        .perform(post(QUESTIONS_URL).contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isGatewayTimeout())
        .andExpect(jsonPath("$.error.code").value("AI_TIMEOUT"));
  }

  private static final String DRAFT_URL = "/api/v1/projects/3/finish/draft";

  @Test
  @DisplayName("33번: 200과 명세 필드(markdown, blanks 배열)를 준다")
  void draft_returns200WithSpecFields() throws Exception {
    when(projectFinishService.createPortfolioDraft(1L, 3L, List.of(71L, 74L, 77L)))
        .thenReturn(
            new PortfolioDraftResponse(
                "# 사자록 · 멋사 회고 서비스\n\n## 내 역할\n(내 역할을 한 문장으로 더해 주세요)\n",
                List.of("role", "artifact")));

    mockMvc
        .perform(
            post(DRAFT_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"recordIds\": [71, 74, 77]}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(
            jsonPath("$.data.markdown")
                .value("# 사자록 · 멋사 회고 서비스\n\n## 내 역할\n(내 역할을 한 문장으로 더해 주세요)\n"))
        .andExpect(jsonPath("$.data.blanks[0]").value("role"))
        .andExpect(jsonPath("$.data.blanks[1]").value("artifact"));
  }

  @Test
  @DisplayName("33번: recordIds가 비면 400 VALIDATION_ERROR와 recordIds 칸의 이유를 주고, 서비스를 부르지 않는다")
  void draft_emptyRecordIds_returns400() throws Exception {
    mockMvc
        .perform(
            post(DRAFT_URL).contentType(MediaType.APPLICATION_JSON).content("{\"recordIds\": []}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
        .andExpect(jsonPath("$.error.fields[0].field").value("recordIds"));
    verifyNoInteractions(projectFinishService);
  }

  @Test
  @DisplayName("33번: 남의 프로젝트·기록이면 404 NOT_FOUND, AI 실패는 502 AI_FAILED, 시간 초과는 504 AI_TIMEOUT")
  void draft_errors_mapToSpecStatuses() throws Exception {
    when(projectFinishService.createPortfolioDraft(anyLong(), anyLong(), anyList()))
        .thenThrow(new BusinessException(ErrorCode.NOT_FOUND))
        .thenThrow(new BusinessException(ErrorCode.AI_FAILED))
        .thenThrow(new BusinessException(ErrorCode.AI_TIMEOUT));
    String body = "{\"recordIds\": [71]}";

    mockMvc
        .perform(post(DRAFT_URL).contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
    mockMvc
        .perform(post(DRAFT_URL).contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isBadGateway())
        .andExpect(jsonPath("$.error.code").value("AI_FAILED"));
    mockMvc
        .perform(post(DRAFT_URL).contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isGatewayTimeout())
        .andExpect(jsonPath("$.error.code").value("AI_TIMEOUT"));
  }
}
