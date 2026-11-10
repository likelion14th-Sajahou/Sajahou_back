package com.sajarok.domain.project.controller;

import com.sajarok.domain.project.dto.FinishQuestionsRequest;
import com.sajarok.domain.project.dto.FinishQuestionsResponse;
import com.sajarok.domain.project.dto.PortfolioDraftRequest;
import com.sajarok.domain.project.dto.PortfolioDraftResponse;
import com.sajarok.domain.project.dto.PortfolioSaveRequest;
import com.sajarok.domain.project.dto.PortfolioSaveResponse;
import com.sajarok.domain.project.service.ProjectFinishService;
import com.sajarok.domain.project.service.ProjectFinishService.FinishQuestions;
import com.sajarok.global.auth.CurrentUserProvider;
import com.sajarok.global.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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

  /** 32번: 마치기 질문 (AI 호출, 최대 50초). 새로 만들면 201, 진행 중인 질문이 있으면 200. */
  @PostMapping("/questions")
  public ResponseEntity<ApiResponse<FinishQuestionsResponse>> createQuestions(
      @PathVariable Long projectId, @Valid @RequestBody FinishQuestionsRequest request) {
    FinishQuestions result =
        projectFinishService.createQuestions(
            currentUserProvider.getUserId(), projectId, request.recordIds());
    return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK)
        .body(ApiResponse.ok(result.response()));
  }

  /** 33번: 포트폴리오 초안 (AI 호출, 최대 50초). 저장하지 않음. 명세대로 200. */
  @PostMapping("/draft")
  public ApiResponse<PortfolioDraftResponse> draft(
      @PathVariable Long projectId, @Valid @RequestBody PortfolioDraftRequest request) {
    return ApiResponse.ok(
        projectFinishService.createPortfolioDraft(
            currentUserProvider.getUserId(), projectId, request.recordIds()));
  }

  /** 34번: 포트폴리오 저장 (처음이면 프로젝트 완료) */
  @PostMapping("/complete")
  public ApiResponse<PortfolioSaveResponse> complete(
      @PathVariable Long projectId, @Valid @RequestBody PortfolioSaveRequest request) {
    return ApiResponse.ok(
        projectFinishService.savePortfolio(
            currentUserProvider.getUserId(), projectId, request.markdown()));
  }
}
