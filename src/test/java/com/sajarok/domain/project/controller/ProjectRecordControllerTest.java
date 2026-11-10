package com.sajarok.domain.project.controller;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sajarok.domain.project.dto.ProjectRecordCreateRequest;
import com.sajarok.domain.project.dto.ProjectRecordResponse;
import com.sajarok.domain.project.entity.ProjectRecordStatus;
import com.sajarok.domain.project.service.ProjectRecordService;
import com.sajarok.global.auth.CurrentUserProvider;
import com.sajarok.global.exception.BusinessException;
import com.sajarok.global.exception.ErrorCode;
import java.time.LocalDate;
import java.util.List;
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
  @DisplayName("27번: DRAFT로 만들면 201과 명세 필드(isAiOrganized 이름, date 형식, cardId null, 빈 배열)를 준다")
  void create_draft_returns201WithSpecFields() throws Exception {
    when(projectRecordService.create(eq(1L), eq(3L), any(ProjectRecordCreateRequest.class)))
        .thenReturn(
            new ProjectRecordResponse(
                77L,
                ProjectRecordStatus.DRAFT,
                null,
                LocalDate.of(2026, 11, 5),
                false,
                "회의에서 API 명세 확정",
                null,
                null,
                null,
                null,
                null,
                List.of(),
                List.of()));

    mockMvc
        .perform(
            post("/api/v1/projects/3/records")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"aiOrganize\": true, \"memo\": \"회의에서 API 명세 확정\"}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.recordId").value(77))
        .andExpect(jsonPath("$.data.status").value("DRAFT"))
        .andExpect(jsonPath("$.data.cardId").value(nullValue()))
        .andExpect(jsonPath("$.data.date").value("2026-11-05"))
        .andExpect(jsonPath("$.data.isAiOrganized").value(false))
        .andExpect(jsonPath("$.data.memo").value("회의에서 API 명세 확정"))
        .andExpect(jsonPath("$.data.nextStep").value(nullValue()))
        .andExpect(jsonPath("$.data.competencies.length()").value(0))
        .andExpect(jsonPath("$.data.attachments.length()").value(0));
  }

  @Test
  @DisplayName("27번: 메모·첨부가 다 없으면 400 EMPTY_MATERIAL")
  void create_emptyMaterial_returns400() throws Exception {
    when(projectRecordService.create(eq(1L), eq(3L), any(ProjectRecordCreateRequest.class)))
        .thenThrow(new BusinessException(ErrorCode.EMPTY_MATERIAL));

    mockMvc
        .perform(
            post("/api/v1/projects/3/records")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"aiOrganize\": true}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("EMPTY_MATERIAL"));
  }

  @Test
  @DisplayName("27번: aiOrganize가 없으면 400 VALIDATION_ERROR와 aiOrganize 칸의 이유(fields)를 준다")
  void create_missingAiOrganize_returns400WithFields() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/projects/3/records")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"memo\": \"메모\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
        .andExpect(jsonPath("$.error.fields[0].field").value("aiOrganize"));
    verifyNoInteractions(projectRecordService);
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
