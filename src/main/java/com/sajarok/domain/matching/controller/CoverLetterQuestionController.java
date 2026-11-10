package com.sajarok.domain.matching.controller;

import com.sajarok.domain.matching.dto.AnswerResponse;
import com.sajarok.domain.matching.dto.EditedAnswerRequest;
import com.sajarok.domain.matching.dto.EditedAnswerResponse;
import com.sajarok.domain.matching.service.CoverLetterQuestionService;
import com.sajarok.global.auth.CurrentUserProvider;
import com.sajarok.global.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/cover-letter-questions")
@RequiredArgsConstructor
public class CoverLetterQuestionController {

  private final CoverLetterQuestionService coverLetterQuestionService;
  private final CurrentUserProvider currentUserProvider;

  /** 37번: 문항별 답 초안 (AI 호출, 최대 50초). 명세대로 200. */
  @PostMapping("/{questionId}/answer")
  public ApiResponse<AnswerResponse> createDraft(@PathVariable Long questionId) {
    return ApiResponse.ok(
        coverLetterQuestionService.createDraft(currentUserProvider.getUserId(), questionId));
  }

  /** 38번: 고친 답 저장 */
  @PatchMapping("/{questionId}")
  public ApiResponse<EditedAnswerResponse> editAnswer(
      @PathVariable Long questionId, @Valid @RequestBody EditedAnswerRequest request) {
    return ApiResponse.ok(
        coverLetterQuestionService.editAnswer(
            currentUserProvider.getUserId(), questionId, request.editedAnswer()));
  }
}
