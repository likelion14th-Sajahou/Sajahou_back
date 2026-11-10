package com.sajarok.domain.matching.controller;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sajarok.domain.matching.dto.EditedAnswerResponse;
import com.sajarok.domain.matching.service.CoverLetterQuestionService;
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
 * 컨트롤러 테스트: 38번 응답 JSON이 API 명세(필드 이름, 상태 코드, 에러 형식)와 같은지 확인한다.
 *
 * <p>보안 설정은 인증 담당 몫이라 테스트에서만 보안 필터를 끈다 (addFilters = false).
 */
@WebMvcTest(CoverLetterQuestionController.class)
@AutoConfigureMockMvc(addFilters = false)
class CoverLetterQuestionControllerTest {

  private static final String URL = "/api/v1/cover-letter-questions/55";

  @Autowired private MockMvc mockMvc;

  @MockitoBean private CoverLetterQuestionService coverLetterQuestionService;
  @MockitoBean private CurrentUserProvider currentUserProvider;

  @BeforeEach
  void setUp() {
    when(currentUserProvider.getUserId()).thenReturn(1L);
  }

  @Test
  @DisplayName("38번: 저장하면 200과 명세의 data 필드(questionId, editedAnswer, charCount)를 준다")
  void editAnswer_returns200WithSpecFields() throws Exception {
    when(coverLetterQuestionService.editAnswer(1L, 55L, "고친 답"))
        .thenReturn(new EditedAnswerResponse(55L, "고친 답", 4));

    mockMvc
        .perform(
            patch(URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"editedAnswer\": \"고친 답\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.questionId").value(55))
        .andExpect(jsonPath("$.data.editedAnswer").value("고친 답"))
        .andExpect(jsonPath("$.data.charCount").value(4));
  }

  @Test
  @DisplayName("38번: editedAnswer를 null로 보내면 초안으로 되돌리고 editedAnswer는 null로 준다")
  void editAnswer_null_returnsNullEditedAnswer() throws Exception {
    when(coverLetterQuestionService.editAnswer(eq(1L), eq(55L), isNull()))
        .thenReturn(new EditedAnswerResponse(55L, null, 412));

    mockMvc
        .perform(
            patch(URL).contentType(MediaType.APPLICATION_JSON).content("{\"editedAnswer\": null}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.editedAnswer").value(nullValue()))
        .andExpect(jsonPath("$.data.charCount").value(412));
  }

  @Test
  @DisplayName("38번: 10,000자를 넘으면 400 VALIDATION_ERROR와 editedAnswer 칸의 이유(fields)를 준다")
  void editAnswer_tooLong_returns400WithFields() throws Exception {
    String tooLong = "가".repeat(10001);

    mockMvc
        .perform(
            patch(URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"editedAnswer\": \"" + tooLong + "\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
        .andExpect(jsonPath("$.error.fields[0].field").value("editedAnswer"))
        .andExpect(jsonPath("$.error.fields[0].reason").exists());
    verifyNoInteractions(coverLetterQuestionService);
  }

  @Test
  @DisplayName("38번: 남의 문항이거나 없으면 404 NOT_FOUND")
  void editAnswer_notFound_returns404() throws Exception {
    when(coverLetterQuestionService.editAnswer(anyLong(), anyLong(), anyString()))
        .thenThrow(new BusinessException(ErrorCode.NOT_FOUND));

    mockMvc
        .perform(
            patch(URL).contentType(MediaType.APPLICATION_JSON).content("{\"editedAnswer\": \"답\"}"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
  }

  @Test
  @DisplayName("38번: 초안을 만들기 전이면 409 INVALID_STATE")
  void editAnswer_noDraft_returns409() throws Exception {
    when(coverLetterQuestionService.editAnswer(anyLong(), anyLong(), anyString()))
        .thenThrow(new BusinessException(ErrorCode.INVALID_STATE));

    mockMvc
        .perform(
            patch(URL).contentType(MediaType.APPLICATION_JSON).content("{\"editedAnswer\": \"답\"}"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.error.code").value("INVALID_STATE"));
  }
}
