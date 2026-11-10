package com.sajarok.domain.project.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.sajarok.domain.project.dto.PortfolioDraftResponse;
import com.sajarok.domain.project.entity.Project;
import com.sajarok.domain.project.entity.ProjectRole;
import com.sajarok.domain.project.entity.ProjectStatus;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/** 33번 포트폴리오 Markdown 조립 테스트: 제목·기간·역할 줄, 항목 순서, 빈칸 문구와 blanks. */
class PortfolioMarkdownTest {

  private static Project project(String subtitle, List<ProjectRole> roles) {
    Project project =
        Project.builder().userId(1L).name("사자록").subtitle(subtitle).roles(roles).build();
    ReflectionTestUtils.setField(project, "createdAt", LocalDateTime.of(2026, 11, 2, 10, 0));
    return project;
  }

  private static Map<String, String> bodies(String contribution, String result) {
    Map<String, String> bodies = new LinkedHashMap<>();
    bodies.put("role", null);
    bodies.put("contribution", contribution);
    bodies.put("problem", null);
    bodies.put("result", result);
    bodies.put("artifact", null);
    bodies.put("learned", null);
    return bodies;
  }

  @Test
  @DisplayName(
      "진행 중: '# 이름 · 부제', '- 기간: 시작일 ~ 진행 중', '- 역할: PM·백엔드' 뒤에 6개 항목이 순서대로, 빈 항목은 프로토타입 문구와 blanks")
  void build_inProgress() {
    PortfolioDraftResponse response =
        PortfolioMarkdown.build(
            project("멋사 회고 서비스", List.of(ProjectRole.PM, ProjectRole.BACKEND)),
            bodies("- 11/03 팀 API 연동을 맡았습니다.", "숫자로 보면: 2주 동안 API 12개를 만들었습니다."));

    assertThat(response.markdown())
        .isEqualTo(
            """
            # 사자록 · 멋사 회고 서비스

            - 기간: 2026.11.02 ~ 진행 중
            - 역할: PM·백엔드

            ## 내 역할
            (내 역할을 한 문장으로 더해 주세요)

            ## 주요 기여
            - 11/03 팀 API 연동을 맡았습니다.

            ## 문제와 해결
            (부딪힌 문제와 해결 방법을 더해 주세요)

            ## 결과
            숫자로 보면: 2주 동안 API 12개를 만들었습니다.

            ## 산출물
            (발표 자료나 시연 링크를 더해 주세요)

            ## 배운 점
            (한 줄 남겨 주세요)
            """);
    assertThat(response.blanks()).containsExactly("role", "problem", "artifact", "learned");
  }

  @Test
  @DisplayName("완료: 기간 끝은 포트폴리오를 저장한 날, 부제가 없으면 제목은 이름만, 역할이 없으면 역할 줄을 빼고, 모두 빈칸이면 blanks 6개")
  void build_doneWithoutSubtitleAndRoles() {
    Project project = project(null, List.of());
    ReflectionTestUtils.setField(project, "status", ProjectStatus.DONE);
    ReflectionTestUtils.setField(
        project, "portfolioSavedAt", LocalDateTime.of(2026, 11, 18, 21, 0));

    PortfolioDraftResponse response = PortfolioMarkdown.build(project, bodies(null, null));

    assertThat(response.markdown())
        .startsWith("# 사자록\n\n- 기간: 2026.11.02 ~ 2026.11.18\n\n## 내 역할\n");
    assertThat(response.markdown()).doesNotContain("- 역할:");
    assertThat(response.blanks())
        .containsExactly("role", "contribution", "problem", "result", "artifact", "learned");
  }

  @Test
  @DisplayName("역할 이름: PLANNING·DESIGN·FRONTEND는 기획·디자인·프론트엔드로, 고른 순서대로 '·'로 잇는다")
  void roles_koreanLabels() {
    assertThat(
            PortfolioMarkdown.roles(
                List.of(ProjectRole.PLANNING, ProjectRole.DESIGN, ProjectRole.FRONTEND)))
        .isEqualTo("기획·디자인·프론트엔드");
  }
}
