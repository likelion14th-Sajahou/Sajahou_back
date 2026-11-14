package com.sajarok.domain.home.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sajarok.domain.home.dto.HomeResponse;
import com.sajarok.domain.home.service.HomeService;
import com.sajarok.domain.project.dto.HomeProjectTiles.DoneTile;
import com.sajarok.domain.project.dto.HomeProjectTiles.InProgressTile;
import com.sajarok.domain.project.entity.ProjectCategory;
import com.sajarok.global.auth.CurrentUserProvider;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** 컨트롤러 테스트: 홈(4번) 프로젝트 타일에 행사 종류(category)가 나가는지 확인한다. 보안 필터는 테스트에서만 끈다. */
@WebMvcTest(HomeController.class)
@AutoConfigureMockMvc(addFilters = false)
class HomeControllerTest {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private HomeService homeService;
  @MockitoBean private CurrentUserProvider currentUserProvider;

  @Test
  @DisplayName("4번: 진행 중·완료 프로젝트 타일에 category를 주고, 기존 필드(id·name·recordCount·lastRecordDate)는 그대로다")
  void home_projectTilesIncludeCategory() throws Exception {
    when(currentUserProvider.getUserId()).thenReturn(1L);
    when(homeService.getHome(1L))
        .thenReturn(
            new HomeResponse(
                null,
                null,
                null,
                null,
                List.of(
                    new InProgressTile(
                        3L, "사자록", ProjectCategory.JUNGKATHON, 5, LocalDate.of(2026, 11, 4))),
                List.of(new DoneTile(1L, "아이디어톤", ProjectCategory.IDEATHON, 8)),
                List.of()));

    mockMvc
        .perform(get("/api/v1/home"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.inProgressProjects[0].id").value(3))
        .andExpect(jsonPath("$.data.inProgressProjects[0].name").value("사자록"))
        .andExpect(jsonPath("$.data.inProgressProjects[0].category").value("JUNGKATHON"))
        .andExpect(jsonPath("$.data.inProgressProjects[0].recordCount").value(5))
        .andExpect(jsonPath("$.data.inProgressProjects[0].lastRecordDate").value("2026-11-04"))
        .andExpect(jsonPath("$.data.doneProjects[0].category").value("IDEATHON"))
        .andExpect(jsonPath("$.data.doneProjects[0].recordCount").value(8));
  }
}
