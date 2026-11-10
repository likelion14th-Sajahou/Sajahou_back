package com.sajarok.domain.question.controller;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sajarok.domain.question.dto.AnswerQuestionRequest;
import com.sajarok.domain.question.dto.AnswerQuestionResponse;
import com.sajarok.domain.question.entity.QuestionStatus;
import com.sajarok.domain.question.service.QuestionAnswerService;
import com.sajarok.global.auth.CurrentUserProvider;
import com.sajarok.global.exception.BusinessException;
import com.sajarok.global.exception.ErrorCode;
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
 * 컨트롤러 테스트: 18번 질문 답하기 API의 요청 검사와 응답 모양이 API 명세와 같은지 확인한다.
 *
 * <p>서비스는 가짜(@MockitoBean)로 바꿔서 컨트롤러만 본다. 보안 설정은 인증 담당 몫이라 테스트에서만 보안 필터를 끈다 (addFilters = false).
 */
@WebMvcTest(QuestionController.class)
@AutoConfigureMockMvc(addFilters = false)
class QuestionControllerTest {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private QuestionAnswerService questionAnswerService;
  @MockitoBean private CurrentUserProvider currentUserProvider;

  @BeforeEach
  void setUp() {
    when(currentUserProvider.getUserId()).thenReturn(1L);
  }

  @Test
  @DisplayName("18번: 답하면 200과 명세 필드(questionId, seq, status, answer)를 준다")
  void answer_returns200WithSpecFields() throws Exception {
    when(questionAnswerService.answer(eq(1L), eq(901L), any(AnswerQuestionRequest.class)))
        .thenReturn(
            new AnswerQuestionResponse(901L, 1, QuestionStatus.ANSWERED, "쿼리가 21개에서 2개로 줄었어요"));

    mockMvc
        .perform(
            patch("/api/v1/questions/901")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\": \"ANSWERED\", \"answer\": \"쿼리가 21개에서 2개로 줄었어요\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.questionId").value(901))
        .andExpect(jsonPath("$.data.seq").value(1))
        .andExpect(jsonPath("$.data.status").value("ANSWERED"))
        .andExpect(jsonPath("$.data.answer").value("쿼리가 21개에서 2개로 줄었어요"));
  }

  @Test
  @DisplayName("18번: 건너뛰면 answer 없이 SKIPPED를 준다")
  void skip_returns200WithoutAnswer() throws Exception {
    when(questionAnswerService.answer(eq(1L), eq(901L), any(AnswerQuestionRequest.class)))
        .thenReturn(new AnswerQuestionResponse(901L, 1, QuestionStatus.SKIPPED, null));

    mockMvc
        .perform(
            patch("/api/v1/questions/901")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\": \"SKIPPED\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.status").value("SKIPPED"))
        .andExpect(jsonPath("$.data.answer").value(nullValue()));
  }

  @Test
  @DisplayName("18번: status가 없으면 400 VALIDATION_ERROR와 status 칸의 이유(fields)를 주고, 서비스는 부르지 않는다")
  void missingStatus_returns400WithField() throws Exception {
    mockMvc
        .perform(
            patch("/api/v1/questions/901")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"answer\": \"답만 보냄\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
        .andExpect(jsonPath("$.error.fields[0].field").value("status"))
        .andExpect(jsonPath("$.error.fields[0].reason").value("답할지 건너뛸지 골라 주세요."));
    verifyNoInteractions(questionAnswerService);
  }

  @Test
  @DisplayName("18번: 답이 10,000자를 넘으면 400 VALIDATION_ERROR와 answer 칸의 이유를 준다")
  void tooLongAnswer_returns400WithField() throws Exception {
    String tooLong = "가".repeat(10_001);

    mockMvc
        .perform(
            patch("/api/v1/questions/901")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\": \"ANSWERED\", \"answer\": \"" + tooLong + "\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
        .andExpect(jsonPath("$.error.fields[0].field").value("answer"));
    verifyNoInteractions(questionAnswerService);
  }

  @Test
  @DisplayName("18번: status가 정해진 값(ANSWERED, SKIPPED, PENDING)이 아니면 400 VALIDATION_ERROR를 준다")
  void unknownStatus_returns400() throws Exception {
    mockMvc
        .perform(
            patch("/api/v1/questions/901")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\": \"DONE\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    verifyNoInteractions(questionAnswerService);
  }

  @Test
  @DisplayName("18번: 없거나 남의 질문이면 404 NOT_FOUND를 준다")
  void notMyQuestion_returns404() throws Exception {
    when(questionAnswerService.answer(eq(1L), eq(999L), any(AnswerQuestionRequest.class)))
        .thenThrow(new BusinessException(ErrorCode.NOT_FOUND));

    mockMvc
        .perform(
            patch("/api/v1/questions/999")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\": \"SKIPPED\"}"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
  }
}
