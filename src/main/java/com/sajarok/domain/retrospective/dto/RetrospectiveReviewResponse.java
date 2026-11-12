package com.sajarok.domain.retrospective.dto;

import com.sajarok.domain.storycard.dto.StoryCardDetailResponse;

/** 19번 응답: { "card": 22번 카드 상세와 같은 모양 } */
public record RetrospectiveReviewResponse(StoryCardDetailResponse card) {}
