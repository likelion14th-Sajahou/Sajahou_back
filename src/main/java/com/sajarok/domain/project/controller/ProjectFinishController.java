package com.sajarok.domain.project.controller;

import com.sajarok.domain.project.dto.PortfolioSaveRequest;
import com.sajarok.domain.project.dto.PortfolioSaveResponse;
import com.sajarok.domain.project.service.ProjectFinishService;
import com.sajarok.global.auth.CurrentUserProvider;
import com.sajarok.global.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 프로젝트 마치기 API (32·33·34번). */
@RestController
@RequestMapping("/api/v1/projects/{projectId}/finish")
@RequiredArgsConstructor
public class ProjectFinishController {

  private final ProjectFinishService projectFinishService;
  private final CurrentUserProvider currentUserProvider;

  /** 34번: 포트폴리오 저장 (처음이면 프로젝트 완료) */
  @PostMapping("/complete")
  public ApiResponse<PortfolioSaveResponse> complete(
      @PathVariable Long projectId, @Valid @RequestBody PortfolioSaveRequest request) {
    return ApiResponse.ok(
        projectFinishService.savePortfolio(
            currentUserProvider.getUserId(), projectId, request.markdown()));
  }
}
