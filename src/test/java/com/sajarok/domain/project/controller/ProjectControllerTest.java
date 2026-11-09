package com.sajarok.domain.project.controller;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sajarok.domain.project.dto.ProjectCreateRequest;
import com.sajarok.domain.project.dto.ProjectCreateResponse;
import com.sajarok.domain.project.dto.ProjectListResponse;
import com.sajarok.domain.project.dto.ProjectListResponse.ProjectSummary;
import com.sajarok.domain.project.entity.ProjectRole;
import com.sajarok.domain.project.entity.ProjectStatus;
import com.sajarok.domain.project.service.ProjectService;
import com.sajarok.global.auth.CurrentUserProvider;
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
 * 컨트롤러 테스트: 응답 JSON이 API 명세(필드 이름, 날짜 형식, 상태 코드, 에러 형식)와 같은지 확인한다.
 *
 * <p>보안 설정은 인증 담당 몫이라 테스트에서만 보안 필터를 끈다 (addFilters = false).
 */
@WebMvcTest(ProjectController.class)
@AutoConfigureMockMvc(addFilters = false)
class ProjectControllerTest {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private ProjectService projectService;
  @MockitoBean private CurrentUserProvider currentUserProvider;

  @BeforeEach
  void setUp() {
    when(currentUserProvider.getUserId()).thenReturn(1L);
  }

  @Test
  @DisplayName("25번: 생성하면 201과 명세의 data 필드(projectId, name, subtitle, roles, status)를 준다")
  void create_returns201WithSpecFields() throws Exception {
    when(projectService.create(eq(1L), any(ProjectCreateRequest.class)))
        .thenReturn(
            new ProjectCreateResponse(
                3L,
                "사자록",
                null,
                List.of(ProjectRole.PM, ProjectRole.BACKEND),
                ProjectStatus.IN_PROGRESS));

    mockMvc
        .perform(
            post("/api/v1/projects")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    { "name": "사자록", "subtitle": null, "roles": ["PM", "BACKEND"] }
                    """))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.error").value(nullValue()))
        .andExpect(jsonPath("$.data.projectId").value(3))
        .andExpect(jsonPath("$.data.name").value("사자록"))
        .andExpect(jsonPath("$.data.subtitle").value(nullValue()))
        .andExpect(jsonPath("$.data.roles[0]").value("PM"))
        .andExpect(jsonPath("$.data.roles[1]").value("BACKEND"))
        .andExpect(jsonPath("$.data.status").value("IN_PROGRESS"));
  }

  @Test
  @DisplayName("25번: 이름이 비고 역할이 없으면 400 VALIDATION_ERROR와 칸별 이유(fields)를 준다")
  void create_invalidInput_returnsValidationErrorWithFields() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/projects")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    { "name": " ", "subtitle": null, "roles": [] }
                    """))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.data").value(nullValue()))
        .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
        .andExpect(jsonPath("$.error.fields.length()").value(2))
        .andExpect(jsonPath("$.error.fields[?(@.field == 'name')].reason").exists())
        .andExpect(jsonPath("$.error.fields[?(@.field == 'roles')].reason").exists());

    verifyNoInteractions(projectService);
  }

  @Test
  @DisplayName("25번: 없는 역할 값을 보내면 400 VALIDATION_ERROR를 준다")
  void create_unknownRole_returnsValidationError() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/projects")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    { "name": "사자록", "roles": ["CEO"] }
                    """))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));

    verifyNoInteractions(projectService);
  }

  @Test
  @DisplayName("43번: 목록은 200과 명세의 필드를 주고, 날짜는 2026-11-04 형식, 기록이 없으면 null이다")
  void getMyProjects_returnsSpecFieldsAndDateFormat() throws Exception {
    when(projectService.getMyProjects(1L))
        .thenReturn(
            new ProjectListResponse(
                List.of(
                    new ProjectSummary(
                        3L,
                        "사자록",
                        "멋사 회고 서비스",
                        List.of(ProjectRole.PM, ProjectRole.BACKEND),
                        5,
                        LocalDate.of(2026, 11, 4))),
                List.of(
                    new ProjectSummary(
                        1L, "아이디어톤", "캠퍼스 중고마켓", List.of(ProjectRole.PLANNING), 0, null))));

    mockMvc
        .perform(get("/api/v1/projects"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.error").value(nullValue()))
        .andExpect(jsonPath("$.data.inProgressProjects[0].projectId").value(3))
        .andExpect(jsonPath("$.data.inProgressProjects[0].name").value("사자록"))
        .andExpect(jsonPath("$.data.inProgressProjects[0].subtitle").value("멋사 회고 서비스"))
        .andExpect(jsonPath("$.data.inProgressProjects[0].roles[0]").value("PM"))
        .andExpect(jsonPath("$.data.inProgressProjects[0].recordCount").value(5))
        .andExpect(jsonPath("$.data.inProgressProjects[0].lastRecordDate").value("2026-11-04"))
        .andExpect(jsonPath("$.data.doneProjects[0].projectId").value(1))
        .andExpect(jsonPath("$.data.doneProjects[0].recordCount").value(0))
        .andExpect(jsonPath("$.data.doneProjects[0].lastRecordDate").value(nullValue()));
  }
}
