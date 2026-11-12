package com.sajarok.domain.retrospective.controller;

import com.sajarok.domain.retrospective.dto.RetrospectiveCompleteResponse;
import com.sajarok.domain.retrospective.dto.RetrospectiveDetailResponse;
import com.sajarok.domain.retrospective.dto.RetrospectiveDraftRequest;
import com.sajarok.domain.retrospective.dto.RetrospectiveProgressResponse;
import com.sajarok.domain.retrospective.dto.RetrospectiveQuestionsRequest;
import com.sajarok.domain.retrospective.dto.RetrospectiveQuestionsResponse;
import com.sajarok.domain.retrospective.dto.RetrospectiveRestRequest;
import com.sajarok.domain.retrospective.dto.RetrospectiveRestResponse;
import com.sajarok.domain.retrospective.dto.RetrospectiveReviewResponse;
import com.sajarok.domain.retrospective.dto.RetrospectiveStartRequest;
import com.sajarok.domain.retrospective.dto.RetrospectiveStartResponse;
import com.sajarok.domain.retrospective.service.RetrospectiveAiService;
import com.sajarok.domain.retrospective.service.RetrospectiveService;
import com.sajarok.global.auth.CurrentUserProvider;
import com.sajarok.global.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
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
  private final RetrospectiveAiService retrospectiveAiService;
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

  /** 13번: 회고 상세 */
  @GetMapping("/{retrospectiveId}")
  public ApiResponse<RetrospectiveDetailResponse> getDetail(@PathVariable Long retrospectiveId) {
    return ApiResponse.ok(
        retrospectiveService.getDetail(currentUserProvider.getUserId(), retrospectiveId));
  }

  /** 14번: 임시저장 */
  @PatchMapping("/{retrospectiveId}")
  public ApiResponse<RetrospectiveProgressResponse> saveDraft(
      @PathVariable Long retrospectiveId, @Valid @RequestBody RetrospectiveDraftRequest request) {
    return ApiResponse.ok(
        retrospectiveService.saveDraft(currentUserProvider.getUserId(), retrospectiveId, request));
  }

  /** 20번: 저장하고 사자록에 추가 */
  @PostMapping("/{retrospectiveId}/complete")
  public ApiResponse<RetrospectiveCompleteResponse> complete(@PathVariable Long retrospectiveId) {
    return ApiResponse.ok(
        retrospectiveService.complete(currentUserProvider.getUserId(), retrospectiveId));
  }

  /** 17번: AI 질문 받기 (AI 호출, 최대 50초). 새로 만들면 201, 저장된 질문·자료 부족은 200, 자료가 바뀌었으면 409 */
  @PostMapping("/{retrospectiveId}/questions")
  public ResponseEntity<ApiResponse<RetrospectiveQuestionsResponse>> createQuestions(
      @PathVariable Long retrospectiveId,
      @RequestBody(required = false) RetrospectiveQuestionsRequest request) {
    RetrospectiveAiService.QuestionsResult result =
        retrospectiveAiService.createQuestions(
            currentUserProvider.getUserId(),
            retrospectiveId,
            request == null ? null : request.mode());
    return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK)
        .body(ApiResponse.ok(result.response()));
  }

  /** 19번: 회고록·카드 초안 만들기 */
  @PostMapping("/{retrospectiveId}/review")
  public ApiResponse<RetrospectiveReviewResponse> review(@PathVariable Long retrospectiveId) {
    return ApiResponse.ok(
        retrospectiveAiService.review(currentUserProvider.getUserId(), retrospectiveId));
  }
}
