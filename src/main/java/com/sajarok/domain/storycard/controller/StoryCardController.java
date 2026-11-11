package com.sajarok.domain.storycard.controller;

import com.sajarok.domain.storycard.dto.StoryCardDetailResponse;
import com.sajarok.domain.storycard.dto.StoryCardListResponse;
import com.sajarok.domain.storycard.dto.StoryCardUpdateRequest;
import com.sajarok.domain.storycard.entity.StoryCardType;
import com.sajarok.domain.storycard.service.StoryCardFacade;
import com.sajarok.global.auth.CurrentUserProvider;
import com.sajarok.global.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/story-cards")
@RequiredArgsConstructor
public class StoryCardController {

  private final StoryCardFacade storyCardFacade;
  private final CurrentUserProvider currentUserProvider;

  /** 24번: 카드 삭제 */
  @DeleteMapping("/{cardId}")
  public ResponseEntity<Void> delete(@PathVariable Long cardId) {
    storyCardFacade.delete(currentUserProvider.getUserId(), cardId);
    return ResponseEntity.noContent().build();
  }

  /** 22번: 카드 상세 */
  @GetMapping("/{cardId}")
  public ApiResponse<StoryCardDetailResponse> getDetail(@PathVariable Long cardId) {
    return ApiResponse.ok(storyCardFacade.getDetail(currentUserProvider.getUserId(), cardId));
  }

  /** 23번: 카드 수정 */
  @PatchMapping("/{cardId}")
  public ApiResponse<StoryCardDetailResponse> update(
      @PathVariable Long cardId, @Valid @RequestBody StoryCardUpdateRequest request) {
    return ApiResponse.ok(storyCardFacade.update(currentUserProvider.getUserId(), cardId, request));
  }

  /** 21번: 카드 목록 */
  @GetMapping
  public ApiResponse<StoryCardListResponse> getList(
      @RequestParam(required = false) StoryCardType type,
      @RequestParam(required = false) Long competencyId,
      @RequestParam(required = false) Integer weekNumber,
      @RequestParam(required = false) String q) {
    return ApiResponse.ok(
        storyCardFacade.getList(
            currentUserProvider.getUserId(), type, competencyId, weekNumber, q));
  }
}
