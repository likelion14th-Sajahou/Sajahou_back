package com.sajarok.domain.project.service;

import com.sajarok.domain.project.dto.ProjectCreateRequest;
import com.sajarok.domain.project.dto.ProjectCreateResponse;
import com.sajarok.domain.project.entity.Project;
import com.sajarok.domain.project.repository.ProjectRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProjectService {

  private final ProjectRepository projectRepository;

  /** 25번: 프로젝트를 만든다. 같은 역할을 여러 번 보내면 하나로 줄인다. 상태는 진행 중(IN_PROGRESS)으로 시작한다. */
  @Transactional
  public ProjectCreateResponse create(Long userId, ProjectCreateRequest request) {
    Project project =
        Project.builder()
            .userId(userId)
            .name(request.name())
            .subtitle(request.subtitle())
            .roles(request.roles().stream().distinct().toList())
            .build();
    return ProjectCreateResponse.from(projectRepository.save(project));
  }
}
