package com.sajarok.domain.project.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sajarok.domain.project.dto.ProjectCreateRequest;
import com.sajarok.domain.project.dto.ProjectCreateResponse;
import com.sajarok.domain.project.entity.Project;
import com.sajarok.domain.project.entity.ProjectRole;
import com.sajarok.domain.project.entity.ProjectStatus;
import com.sajarok.domain.project.repository.ProjectRepository;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProjectServiceTest {

  @Mock private ProjectRepository projectRepository;

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
}
