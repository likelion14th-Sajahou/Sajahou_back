package com.sajarok.domain.retrospective.controller;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sajarok.domain.retrospective.dto.RetrospectiveProgressResponse;
import com.sajarok.domain.retrospective.dto.RetrospectiveRestResponse;
import com.sajarok.domain.retrospective.dto.RetrospectiveStartRequest;
import com.sajarok.domain.retrospective.dto.RetrospectiveStartResponse;
import com.sajarok.domain.retrospective.dto.RetrospectiveState;
import com.sajarok.domain.retrospective.entity.RetrospectiveType;
import com.sajarok.domain.retrospective.service.RetrospectiveService;
import com.sajarok.domain.retrospective.service.RetrospectiveService.StartResult;
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
 * 컨트롤러 테스트: 회고 API의 상태 코드와 응답 모양이 API 명세와 같은지 확인한다.
 *
 * <p>서비스는 가짜(@MockitoBean)로 바꾸고, 보안 필터는 테스트에서만 끈다 (addFilters = false).
 */
@WebMvcTest(RetrospectiveController.class)
@AutoConfigureMockMvc(addFilters = false)
class RetrospectiveControllerTest {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private RetrospectiveService retrospectiveService;
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
}
