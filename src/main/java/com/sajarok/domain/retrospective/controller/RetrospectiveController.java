package com.sajarok.domain.retrospective.controller;

import com.sajarok.domain.retrospective.dto.RetrospectiveProgressResponse;
import com.sajarok.domain.retrospective.dto.RetrospectiveRestRequest;
import com.sajarok.domain.retrospective.dto.RetrospectiveRestResponse;
import com.sajarok.domain.retrospective.dto.RetrospectiveStartRequest;
import com.sajarok.domain.retrospective.dto.RetrospectiveStartResponse;
import com.sajarok.domain.retrospective.service.RetrospectiveService;
import com.sajarok.global.auth.CurrentUserProvider;
import com.sajarok.global.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/retrospectives")
@RequiredArgsConstructor
public class RetrospectiveController {

  private final RetrospectiveService retrospectiveService;
  private final CurrentUserProvider currentUserProvider;

  /** 12번: 회고 시작. 새로 만들면 201, 이미 있으면 200 */
  @PostMapping
  public ResponseEntity<ApiResponse<RetrospectiveStartResponse>> start(
      @Valid @RequestBody RetrospectiveStartRequest request) {
    RetrospectiveService.StartResult result =
        retrospectiveService.start(currentUserProvider.getUserId(), request);
    return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK)
        .body(ApiResponse.ok(result.response()));
  }

  /** 15번: 휴회 표시·해제 */
  @PatchMapping("/{retrospectiveId}/rest")
  public ApiResponse<RetrospectiveRestResponse> changeRested(
      @PathVariable Long retrospectiveId, @Valid @RequestBody RetrospectiveRestRequest request) {
    return ApiResponse.ok(
        retrospectiveService.changeRested(
            currentUserProvider.getUserId(), retrospectiveId, request.rested()));
  }

  /** 16번: 처음부터 */
  @PostMapping("/{retrospectiveId}/reset")
  public ApiResponse<RetrospectiveProgressResponse> reset(@PathVariable Long retrospectiveId) {
    return ApiResponse.ok(
        retrospectiveService.reset(currentUserProvider.getUserId(), retrospectiveId));
  }
}
