package com.sajarok.domain.project.dto;

import com.sajarok.domain.project.entity.Project;
import com.sajarok.domain.project.entity.ProjectCategory;
import com.sajarok.domain.project.entity.ProjectRole;
import com.sajarok.domain.project.entity.ProjectStatus;
import java.util.List;

/** 25번 프로젝트 생성 응답. */
public record ProjectCreateResponse(
    Long projectId,
    String name,
    String subtitle,
    ProjectCategory category,
    List<ProjectRole> roles,
    ProjectStatus status) {

  public static ProjectCreateResponse from(Project project) {
    return new ProjectCreateResponse(
        project.getId(),
        project.getName(),
        project.getSubtitle(),
        project.getCategory(),
        List.copyOf(project.getRoles()),
        project.getStatus());
  }
}
