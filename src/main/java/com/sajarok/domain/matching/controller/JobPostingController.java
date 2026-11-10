package com.sajarok.domain.matching.controller;

import com.sajarok.domain.matching.dto.JobPostingCreateRequest;
import com.sajarok.domain.matching.dto.JobPostingCreateResponse;
import com.sajarok.domain.matching.dto.JobPostingDetailResponse;
import com.sajarok.domain.matching.service.JobPostingService;
import com.sajarok.global.auth.CurrentUserProvider;
import com.sajarok.global.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/job-postings")
@RequiredArgsConstructor
public class JobPostingController {

  private final JobPostingService jobPostingService;
  private final CurrentUserProvider currentUserProvider;

  /** 36번: 공고 저장·역량 찾기 (AI 호출, 최대 50초). 성공하면 201. */
  @PostMapping
  public ResponseEntity<ApiResponse<JobPostingCreateResponse>> create(
      @Valid @RequestBody JobPostingCreateRequest request) {
    JobPostingCreateResponse response =
        jobPostingService.create(currentUserProvider.getUserId(), request);
    return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(response));
  }

  /** 39번: 매칭 결과 조회 */
  @GetMapping("/{jobPostingId}")
  public ApiResponse<JobPostingDetailResponse> getDetail(@PathVariable Long jobPostingId) {
    return ApiResponse.ok(
        jobPostingService.getDetail(currentUserProvider.getUserId(), jobPostingId));
  }
}
