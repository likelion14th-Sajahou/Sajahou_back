package com.sajarok.domain.question.controller;

import com.sajarok.domain.question.dto.AnswerQuestionRequest;
import com.sajarok.domain.question.dto.AnswerQuestionResponse;
import com.sajarok.domain.question.service.QuestionAnswerService;
import com.sajarok.global.auth.CurrentUserProvider;
import com.sajarok.global.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/questions")
@RequiredArgsConstructor
public class QuestionController {

  private final QuestionAnswerService questionAnswerService;
  private final CurrentUserProvider currentUserProvider;

  /** 18번: 질문 답하기·건너뛰기 */
  @PatchMapping("/{questionId}")
  public ApiResponse<AnswerQuestionResponse> answer(
      @PathVariable Long questionId, @Valid @RequestBody AnswerQuestionRequest request) {
    return ApiResponse.ok(
        questionAnswerService.answer(currentUserProvider.getUserId(), questionId, request));
  }
}
