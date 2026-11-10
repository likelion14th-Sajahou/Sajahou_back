package com.sajarok.domain.project.controller;

import com.sajarok.domain.project.dto.ProjectCreateRequest;
import com.sajarok.domain.project.dto.ProjectCreateResponse;
import com.sajarok.domain.project.dto.ProjectListResponse;
import com.sajarok.domain.project.service.ProjectService;
import com.sajarok.global.auth.CurrentUserProvider;
import com.sajarok.global.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/projects")
@RequiredArgsConstructor
public class ProjectController {

  private final ProjectService projectService;
  private final CurrentUserProvider currentUserProvider;

  /** 25번: 프로젝트 생성 */
  @PostMapping
  public ResponseEntity<ApiResponse<ProjectCreateResponse>> create(
      @Valid @RequestBody ProjectCreateRequest request) {
    ProjectCreateResponse response =
        projectService.create(currentUserProvider.getUserId(), request);
    return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(response));
  }

  /** 43번: 내 프로젝트 목록 */
  @GetMapping
  public ApiResponse<ProjectListResponse> getMyProjects() {
    return ApiResponse.ok(projectService.getMyProjects(currentUserProvider.getUserId()));
  }
}
