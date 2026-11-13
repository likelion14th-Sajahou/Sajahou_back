package com.sajarok.domain.project.controller;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sajarok.domain.project.dto.ProjectCreateRequest;
import com.sajarok.domain.project.dto.ProjectCreateResponse;
import com.sajarok.domain.project.dto.ProjectDetailResponse;
import com.sajarok.domain.project.dto.ProjectListResponse;
import com.sajarok.domain.project.dto.ProjectListResponse.ProjectSummary;
import com.sajarok.domain.project.entity.ProjectCategory;
import com.sajarok.domain.project.entity.ProjectRole;
import com.sajarok.domain.project.entity.ProjectStatus;
import com.sajarok.domain.project.service.ProjectService;
import com.sajarok.global.auth.CurrentUserProvider;
import com.sajarok.global.exception.BusinessException;
import com.sajarok.global.exception.ErrorCode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
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
  @DisplayName("25번: 생성하면 201과 명세의 data 필드(projectId, name, subtitle, category, roles, status)를 준다")
  void create_returns201WithSpecFields() throws Exception {
    when(projectService.create(eq(1L), any(ProjectCreateRequest.class)))
        .thenReturn(
            new ProjectCreateResponse(
                3L,
                "사자록",
                null,
                ProjectCategory.JUNGKATHON,
                List.of(ProjectRole.PM, ProjectRole.BACKEND),
                ProjectStatus.IN_PROGRESS));

    mockMvc
        .perform(
            post("/api/v1/projects")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "name": "사자록",
                      "subtitle": null,
                      "category": "JUNGKATHON",
                      "roles": ["PM", "BACKEND"]
                    }
                    """))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.error").value(nullValue()))
        .andExpect(jsonPath("$.data.projectId").value(3))
        .andExpect(jsonPath("$.data.name").value("사자록"))
        .andExpect(jsonPath("$.data.subtitle").value(nullValue()))
        .andExpect(jsonPath("$.data.category").value("JUNGKATHON"))
        .andExpect(jsonPath("$.data.roles[0]").value("PM"))
        .andExpect(jsonPath("$.data.roles[1]").value("BACKEND"))
        .andExpect(jsonPath("$.data.status").value("IN_PROGRESS"));
  }

  @Test
  @DisplayName("25번: 이름이 비고 행사·역할이 없으면 400 VALIDATION_ERROR와 칸별 이유(fields)를 준다")
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
        .andExpect(jsonPath("$.error.fields.length()").value(3))
        .andExpect(jsonPath("$.error.fields[?(@.field == 'name')].reason").exists())
        .andExpect(jsonPath("$.error.fields[?(@.field == 'category')].reason").exists())
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
                    { "name": "사자록", "category": "JUNGKATHON", "roles": ["CEO"] }
                    """))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));

    verifyNoInteractions(projectService);
  }

  @Test
  @DisplayName("25번: 행사가 아이디어톤·해커톤·중커톤이 아니면(예: OTHER) 400 VALIDATION_ERROR를 준다")
  void create_unknownCategory_returnsValidationError() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/projects")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    { "name": "사자록", "category": "OTHER", "roles": ["PM"] }
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
                        ProjectCategory.JUNGKATHON,
                        List.of(ProjectRole.PM, ProjectRole.BACKEND),
                        5,
                        LocalDate.of(2026, 11, 4))),
                List.of(
                    new ProjectSummary(
                        1L,
                        "아이디어톤",
                        "캠퍼스 중고마켓",
                        ProjectCategory.IDEATHON,
                        List.of(ProjectRole.PLANNING),
                        0,
                        null))));

    mockMvc
        .perform(get("/api/v1/projects"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.error").value(nullValue()))
        .andExpect(jsonPath("$.data.inProgressProjects[0].projectId").value(3))
        .andExpect(jsonPath("$.data.inProgressProjects[0].name").value("사자록"))
        .andExpect(jsonPath("$.data.inProgressProjects[0].subtitle").value("멋사 회고 서비스"))
        .andExpect(jsonPath("$.data.inProgressProjects[0].category").value("JUNGKATHON"))
        .andExpect(jsonPath("$.data.inProgressProjects[0].roles[0]").value("PM"))
        .andExpect(jsonPath("$.data.inProgressProjects[0].recordCount").value(5))
        .andExpect(jsonPath("$.data.inProgressProjects[0].lastRecordDate").value("2026-11-04"))
        .andExpect(jsonPath("$.data.doneProjects[0].projectId").value(1))
        .andExpect(jsonPath("$.data.doneProjects[0].recordCount").value(0))
        .andExpect(jsonPath("$.data.doneProjects[0].lastRecordDate").value(nullValue()));
  }

  @Test
  @DisplayName("26번: 상세는 200과 명세 필드(startedOn·date 날짜 형식, savedAt +09:00, cardId)를 준다")
  void getDetail_returns200WithSpecFields() throws Exception {
    when(projectService.getDetail(1L, 3L))
        .thenReturn(
            new ProjectDetailResponse(
                3L,
                "사자록",
                "멋사 회고 서비스",
                ProjectCategory.JUNGKATHON,
                List.of(ProjectRole.PM, ProjectRole.BACKEND),
                ProjectStatus.DONE,
                LocalDate.of(2026, 10, 20),
                1,
                LocalDate.of(2026, 11, 4),
                new ProjectDetailResponse.Portfolio(
                    "# 사자록", OffsetDateTime.parse("2026-11-18T21:00:00+09:00")),
                List.of(
                    new ProjectDetailResponse.RecordItem(
                        77L,
                        null,
                        LocalDate.of(2026, 11, 4),
                        true,
                        "메모",
                        "한 일",
                        null,
                        null,
                        null,
                        null,
                        List.of(),
                        List.of()))));

    mockMvc
        .perform(get("/api/v1/projects/3"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.projectId").value(3))
        .andExpect(jsonPath("$.data.category").value("JUNGKATHON"))
        .andExpect(jsonPath("$.data.roles[0]").value("PM"))
        .andExpect(jsonPath("$.data.status").value("DONE"))
        .andExpect(jsonPath("$.data.startedOn").value("2026-10-20"))
        .andExpect(jsonPath("$.data.recordCount").value(1))
        .andExpect(jsonPath("$.data.lastRecordDate").value("2026-11-04"))
        .andExpect(jsonPath("$.data.portfolio.markdown").value("# 사자록"))
        .andExpect(jsonPath("$.data.portfolio.savedAt").value("2026-11-18T21:00:00+09:00"))
        .andExpect(jsonPath("$.data.records[0].recordId").value(77))
        .andExpect(jsonPath("$.data.records[0].cardId").value(nullValue()))
        .andExpect(jsonPath("$.data.records[0].date").value("2026-11-04"))
        .andExpect(jsonPath("$.data.records[0].isAiOrganized").value(true))
        .andExpect(jsonPath("$.data.records[0].competencies.length()").value(0))
        .andExpect(jsonPath("$.data.records[0].attachments.length()").value(0));
  }

  @Test
  @DisplayName("26번: 포트폴리오가 없으면 portfolio는 null, 남의 프로젝트면 404 NOT_FOUND")
  void getDetail_nullPortfolio_and404() throws Exception {
    when(projectService.getDetail(1L, 3L))
        .thenReturn(
            new ProjectDetailResponse(
                3L,
                "사자록",
                null,
                ProjectCategory.JUNGKATHON,
                List.of(ProjectRole.BACKEND),
                ProjectStatus.IN_PROGRESS,
                LocalDate.of(2026, 10, 20),
                0,
                null,
                null,
                List.of()));
    when(projectService.getDetail(1L, 999L)).thenThrow(new BusinessException(ErrorCode.NOT_FOUND));

    mockMvc
        .perform(get("/api/v1/projects/3"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.portfolio").value(nullValue()))
        .andExpect(jsonPath("$.data.lastRecordDate").value(nullValue()))
        .andExpect(jsonPath("$.data.records.length()").value(0));
    mockMvc
        .perform(get("/api/v1/projects/999"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
  }

  @Test
  @DisplayName("44번: 지우면 204와 빈 본문을 주고, 로그인 회원 ID로 서비스를 부른다")
  void delete_returns204WithEmptyBody() throws Exception {
    mockMvc
        .perform(delete("/api/v1/projects/3"))
        .andExpect(status().isNoContent())
        .andExpect(content().string(""));
    verify(projectService).delete(1L, 3L);
  }

  @Test
  @DisplayName("44번: 남의 프로젝트거나 없으면 404 NOT_FOUND")
  void delete_notMine_returns404() throws Exception {
    doThrow(new BusinessException(ErrorCode.NOT_FOUND)).when(projectService).delete(1L, 3L);

    mockMvc
        .perform(delete("/api/v1/projects/3"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
  }
}
