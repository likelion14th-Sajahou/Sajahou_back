package com.sajarok.domain.project.controller;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sajarok.domain.project.dto.ProjectRecordCreateRequest;
import com.sajarok.domain.project.dto.ProjectRecordResponse;
import com.sajarok.domain.project.dto.ProjectRecordUpdateRequest;
import com.sajarok.domain.project.dto.RecordOrganizeResponse;
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
  @DisplayName("27번 '이대로 저장': 5칸·첨부가 다 없으면 400 EMPTY_MATERIAL")
  void create_emptyMaterial_returns400() throws Exception {
    when(projectRecordService.create(eq(1L), eq(3L), any(ProjectRecordCreateRequest.class)))
        .thenThrow(new BusinessException(ErrorCode.EMPTY_MATERIAL));

    mockMvc
        .perform(
            post("/api/v1/projects/3/records")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"aiOrganize\": false, \"competencyIds\": [3]}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("EMPTY_MATERIAL"))
        .andExpect(jsonPath("$.error.detail").doesNotExist());
  }

  @Test
  @DisplayName("27번: 역량 태그가 1~3개가 아니면 400 COMPETENCY_LIMIT")
  void create_competencyLimit_returns400() throws Exception {
    when(projectRecordService.create(eq(1L), eq(3L), any(ProjectRecordCreateRequest.class)))
        .thenThrow(new BusinessException(ErrorCode.COMPETENCY_LIMIT));

    mockMvc
        .perform(
            post("/api/v1/projects/3/records")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"aiOrganize\": false, \"did\": \"한 일\", \"competencyIds\": []}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.error.code").value("COMPETENCY_LIMIT"));
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
  @DisplayName("28번: 고치면 200과 명세 필드(status, isAiOrganized, memo 등)를 주고, 요청의 isAiOrganized를 읽는다")
  void update_returns200WithSpecFields() throws Exception {
    when(projectRecordService.update(eq(1L), eq(77L), any(ProjectRecordUpdateRequest.class)))
        .thenAnswer(
            i -> {
              ProjectRecordUpdateRequest req = i.getArgument(2);
              return new ProjectRecordResponse(
                  77L,
                  ProjectRecordStatus.SAVED,
                  null,
                  LocalDate.of(2026, 11, 5),
                  req.isAiOrganized(),
                  req.memo(),
                  "API 명세 확정",
                  null,
                  null,
                  null,
                  null,
                  List.of(),
                  List.of());
            });

    mockMvc
        .perform(
            patch("/api/v1/project-records/77")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"memo\": \"새 메모\", \"isAiOrganized\": true}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.recordId").value(77))
        .andExpect(jsonPath("$.data.status").value("SAVED"))
        .andExpect(jsonPath("$.data.date").value("2026-11-05"))
        .andExpect(jsonPath("$.data.isAiOrganized").value(true))
        .andExpect(jsonPath("$.data.memo").value("새 메모"));
  }

  @Test
  @DisplayName("28번: 남의 기록이거나 없으면 404 NOT_FOUND")
  void update_notMine_returns404() throws Exception {
    when(projectRecordService.update(eq(1L), eq(77L), any(ProjectRecordUpdateRequest.class)))
        .thenThrow(new BusinessException(ErrorCode.NOT_FOUND));

    mockMvc
        .perform(
            patch("/api/v1/project-records/77")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"memo\": \"새 메모\"}"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
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

  private static final String ORGANIZE_URL = "/api/v1/project-records/77/organize";

  @Test
  @DisplayName(
      "31번: 200과 명세 필드(did·concern·decision·result·nextStep, 빈 칸은 null로 나옴, blanks 배열)를 준다")
  void organize_returns200WithSpecFields() throws Exception {
    when(projectRecordService.organize(1L, 77L))
        .thenReturn(
            new RecordOrganizeResponse(
                "시안 2개를 준비해 비교 발표했다.",
                "개발자와 홈 화면 구성 의견이 달랐다.",
                null,
                "물어본 3명 모두 두 번째 시안이 '할 일이 바로 보인다'고 했다.",
                null,
                List.of("decision", "nextStep")));

    mockMvc
        .perform(post(ORGANIZE_URL))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.did").value("시안 2개를 준비해 비교 발표했다."))
        .andExpect(jsonPath("$.data.concern").value("개발자와 홈 화면 구성 의견이 달랐다."))
        .andExpect(jsonPath("$.data.decision").value(nullValue()))
        .andExpect(jsonPath("$.data.result").value("물어본 3명 모두 두 번째 시안이 '할 일이 바로 보인다'고 했다."))
        .andExpect(jsonPath("$.data.nextStep").value(nullValue()))
        .andExpect(jsonPath("$.data.blanks[0]").value("decision"))
        .andExpect(jsonPath("$.data.blanks[1]").value("nextStep"));
  }

  @Test
  @DisplayName(
      "31번: 자료 없음 400 EMPTY_MATERIAL, 남의 기록 404 NOT_FOUND, AI 실패 502 AI_FAILED, 시간 초과 504 AI_TIMEOUT")
  void organize_errors_mapToSpecStatuses() throws Exception {
    when(projectRecordService.organize(1L, 77L))
        .thenThrow(new BusinessException(ErrorCode.EMPTY_MATERIAL))
        .thenThrow(new BusinessException(ErrorCode.NOT_FOUND))
        .thenThrow(new BusinessException(ErrorCode.AI_FAILED))
        .thenThrow(new BusinessException(ErrorCode.AI_TIMEOUT));

    mockMvc
        .perform(post(ORGANIZE_URL))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("EMPTY_MATERIAL"));
    mockMvc
        .perform(post(ORGANIZE_URL))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
    mockMvc
        .perform(post(ORGANIZE_URL))
        .andExpect(status().isBadGateway())
        .andExpect(jsonPath("$.error.code").value("AI_FAILED"));
    mockMvc
        .perform(post(ORGANIZE_URL))
        .andExpect(status().isGatewayTimeout())
        .andExpect(jsonPath("$.error.code").value("AI_TIMEOUT"));
  }
}
