package com.sajarok.domain.matching.controller;

import com.sajarok.domain.matching.dto.JobPostingDetailResponse;
import com.sajarok.domain.matching.service.JobPostingService;
import com.sajarok.global.auth.CurrentUserProvider;
import com.sajarok.global.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/job-postings")
@RequiredArgsConstructor
public class JobPostingController {

  private final JobPostingService jobPostingService;
  private final CurrentUserProvider currentUserProvider;

  /** 39번: 매칭 결과 조회 */
  @GetMapping("/{jobPostingId}")
  public ApiResponse<JobPostingDetailResponse> getDetail(@PathVariable Long jobPostingId) {
    return ApiResponse.ok(
        jobPostingService.getDetail(currentUserProvider.getUserId(), jobPostingId));
  }
}
