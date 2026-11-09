package com.sajarok.domain.project.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sajarok.domain.project.dto.HomeProjectTiles;
import com.sajarok.domain.project.dto.HomeProjectTiles.DoneTile;
import com.sajarok.domain.project.dto.HomeProjectTiles.InProgressTile;
import com.sajarok.domain.project.dto.ProjectCreateRequest;
import com.sajarok.domain.project.dto.ProjectCreateResponse;
import com.sajarok.domain.project.dto.ProjectListResponse;
import com.sajarok.domain.project.dto.ProjectListResponse.ProjectSummary;
import com.sajarok.domain.project.entity.Project;
import com.sajarok.domain.project.entity.ProjectRecordStatus;
import com.sajarok.domain.project.entity.ProjectRole;
import com.sajarok.domain.project.entity.ProjectStatus;
import com.sajarok.domain.project.repository.ProjectRecordRepository;
import com.sajarok.domain.project.repository.ProjectRepository;
import com.sajarok.domain.project.repository.RecordStats;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ProjectServiceTest {

  @Mock private ProjectRepository projectRepository;
  @Mock private ProjectRecordRepository projectRecordRepository;

  @InjectMocks private ProjectService projectService;

  @Test
  @DisplayName("프로젝트를 만들면 로그인 회원 것으로, 진행 중 상태로 저장되고 중복 역할은 하나로 줄어든다")
  void create_savesInProgressProjectWithDistinctRoles() {
    when(projectRepository.save(any(Project.class))).thenAnswer(inv -> inv.getArgument(0));
    ProjectCreateRequest request =
        new ProjectCreateRequest(
            "사자록", "멋사 회고 서비스", List.of(ProjectRole.PM, ProjectRole.BACKEND, ProjectRole.PM));

    ProjectCreateResponse response = projectService.create(1L, request);

    ArgumentCaptor<Project> saved = ArgumentCaptor.forClass(Project.class);
    verify(projectRepository).save(saved.capture());
    assertThat(saved.getValue().getUserId()).isEqualTo(1L);
    assertThat(saved.getValue().getStatus()).isEqualTo(ProjectStatus.IN_PROGRESS);
    assertThat(saved.getValue().getRoles()).containsExactly(ProjectRole.PM, ProjectRole.BACKEND);
    assertThat(response.name()).isEqualTo("사자록");
    assertThat(response.subtitle()).isEqualTo("멋사 회고 서비스");
    assertThat(response.roles()).containsExactly(ProjectRole.PM, ProjectRole.BACKEND);
    assertThat(response.status()).isEqualTo(ProjectStatus.IN_PROGRESS);
  }

  @Test
  @DisplayName("목록은 진행 중·완료로 나뉘고, 저장된 기록 수와 마지막 기록 날짜가 붙으며, 기록이 없으면 0과 null이다")
  void getMyProjects_splitsByStatusAndFillsRecordStats() {
    Project inProgress = project(3L, "사자록", ProjectStatus.IN_PROGRESS);
    Project done = project(1L, "아이디어톤", ProjectStatus.DONE);
    when(projectRepository.findAllByUserIdOrderByCreatedAtDescIdDesc(1L))
        .thenReturn(List.of(inProgress, done));
    when(projectRecordRepository.findStats(List.of(3L, 1L), ProjectRecordStatus.SAVED))
        .thenReturn(List.of(new RecordStats(3L, 5L, LocalDateTime.of(2026, 11, 4, 21, 30))));

    ProjectListResponse response = projectService.getMyProjects(1L);

    assertThat(response.inProgressProjects())
        .containsExactly(
            new ProjectSummary(
                3L, "사자록", null, List.of(ProjectRole.BACKEND), 5, LocalDate.of(2026, 11, 4)));
    assertThat(response.doneProjects())
        .containsExactly(
            new ProjectSummary(1L, "아이디어톤", null, List.of(ProjectRole.BACKEND), 0, null));
  }

  @Test
  @DisplayName("프로젝트가 하나도 없으면 빈 목록을 주고 기록 수 쿼리는 부르지 않는다")
  void getMyProjects_noProjects_returnsEmptyListsWithoutStatsQuery() {
    when(projectRepository.findAllByUserIdOrderByCreatedAtDescIdDesc(1L)).thenReturn(List.of());

    ProjectListResponse response = projectService.getMyProjects(1L);

    assertThat(response.inProgressProjects()).isEmpty();
    assertThat(response.doneProjects()).isEmpty();
    verify(projectRecordRepository, never()).findStats(anyCollection(), any());
  }

  @Test
  @DisplayName("홈 타일은 4번 형태대로 진행 중에는 마지막 기록 날짜를, 완료에는 기록 수만 넣는다")
  void homeTiles_matchesHomeSpecShape() {
    Project inProgress = project(3L, "사자록", ProjectStatus.IN_PROGRESS);
    Project done = project(1L, "아이디어톤", ProjectStatus.DONE);
    when(projectRepository.findAllByUserIdOrderByCreatedAtDescIdDesc(1L))
        .thenReturn(List.of(inProgress, done));
    when(projectRecordRepository.findStats(List.of(3L, 1L), ProjectRecordStatus.SAVED))
        .thenReturn(
            List.of(
                new RecordStats(3L, 5L, LocalDateTime.of(2026, 11, 4, 10, 0)),
                new RecordStats(1L, 8L, LocalDateTime.of(2026, 5, 26, 10, 0))));

    HomeProjectTiles tiles = projectService.homeTiles(1L);

    assertThat(tiles.inProgressProjects())
        .containsExactly(new InProgressTile(3L, "사자록", 5, LocalDate.of(2026, 11, 4)));
    assertThat(tiles.doneProjects()).containsExactly(new DoneTile(1L, "아이디어톤", 8));
  }

  /** 테스트용 프로젝트. id와 상태는 DB나 엔티티 메서드가 정하므로 테스트에서는 직접 넣는다. */
  private static Project project(Long id, String name, ProjectStatus status) {
    Project project =
        Project.builder().userId(1L).name(name).roles(List.of(ProjectRole.BACKEND)).build();
    ReflectionTestUtils.setField(project, "id", id);
    ReflectionTestUtils.setField(project, "status", status);
    return project;
  }
}
