package com.sajarok.domain.project.controller;

import com.sajarok.domain.project.dto.ProjectRecordCreateRequest;
import com.sajarok.domain.project.dto.ProjectRecordResponse;
import com.sajarok.domain.project.service.ProjectRecordService;
import com.sajarok.global.auth.CurrentUserProvider;
import com.sajarok.global.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 프로젝트 기록 API (27·28·29번).
 *
 * <p>27번은 /projects/{projectId}/records, 28·29번은 /project-records/{recordId}로 URL이 다르지만, 같은 기록 기능이라
 * 한 컨트롤러에 둔다.
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class ProjectRecordController {

  private final ProjectRecordService projectRecordService;
  private final CurrentUserProvider currentUserProvider;

  /** 27번: 기록 추가. 성공하면 201. */
  @PostMapping("/projects/{projectId}/records")
  public ResponseEntity<ApiResponse<ProjectRecordResponse>> create(
      @PathVariable Long projectId, @Valid @RequestBody ProjectRecordCreateRequest request) {
    ProjectRecordResponse response =
        projectRecordService.create(currentUserProvider.getUserId(), projectId, request);
    return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(response));
  }

  /** 29번: 기록 삭제. 이 기록의 프로젝트 카드도 함께 지운다. 성공하면 204 (본문 없음). */
  @DeleteMapping("/project-records/{recordId}")
  public ResponseEntity<Void> delete(@PathVariable Long recordId) {
    projectRecordService.delete(currentUserProvider.getUserId(), recordId);
    return ResponseEntity.noContent().build();
  }
}
