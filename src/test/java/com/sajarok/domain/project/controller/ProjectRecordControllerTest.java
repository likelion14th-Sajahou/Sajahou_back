package com.sajarok.domain.project.controller;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sajarok.domain.project.service.ProjectRecordService;
import com.sajarok.global.auth.CurrentUserProvider;
import com.sajarok.global.exception.BusinessException;
import com.sajarok.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 컨트롤러 테스트: 기록 API 응답이 API 명세(상태 코드, 에러 형식)와 같은지 확인한다.
 *
 * <p>보안 설정은 인증 담당 몫이라 테스트에서만 보안 필터를 끈다 (addFilters = false).
 */
@WebMvcTest(ProjectRecordController.class)
@AutoConfigureMockMvc(addFilters = false)
class ProjectRecordControllerTest {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private ProjectRecordService projectRecordService;
  @MockitoBean private CurrentUserProvider currentUserProvider;

  @BeforeEach
  void setUp() {
    when(currentUserProvider.getUserId()).thenReturn(1L);
  }

  @Test
  @DisplayName("29번: 지우면 204와 빈 본문을 주고, 로그인 회원 ID로 서비스를 부른다")
  void delete_returns204WithEmptyBody() throws Exception {
    mockMvc
        .perform(delete("/api/v1/project-records/77"))
        .andExpect(status().isNoContent())
        .andExpect(content().string(""));
    verify(projectRecordService).delete(1L, 77L);
  }

  @Test
  @DisplayName("29번: 남의 기록이거나 없으면 404 NOT_FOUND")
  void delete_notMine_returns404() throws Exception {
    doThrow(new BusinessException(ErrorCode.NOT_FOUND)).when(projectRecordService).delete(1L, 77L);

    mockMvc
        .perform(delete("/api/v1/project-records/77"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
  }

  @Test
  @DisplayName("29번: 경로의 기록 ID가 숫자가 아니면 400 VALIDATION_ERROR")
  void delete_nonNumericId_returns400() throws Exception {
    mockMvc
        .perform(delete("/api/v1/project-records/abc"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    verifyNoInteractions(projectRecordService);
  }
}
