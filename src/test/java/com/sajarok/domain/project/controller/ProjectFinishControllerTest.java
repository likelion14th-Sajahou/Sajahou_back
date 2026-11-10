package com.sajarok.domain.project.controller;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sajarok.domain.project.dto.PortfolioSaveResponse;
import com.sajarok.domain.project.dto.ProjectDetailResponse.Portfolio;
import com.sajarok.domain.project.entity.ProjectStatus;
import com.sajarok.domain.project.service.ProjectFinishService;
import com.sajarok.global.auth.CurrentUserProvider;
import com.sajarok.global.exception.BusinessException;
import com.sajarok.global.exception.ErrorCode;
import java.time.OffsetDateTime;
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
 * 컨트롤러 테스트: 34번 응답 JSON이 API 명세(필드 이름, 날짜 형식, 상태 코드, 에러 형식)와 같은지 확인한다.
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
}
