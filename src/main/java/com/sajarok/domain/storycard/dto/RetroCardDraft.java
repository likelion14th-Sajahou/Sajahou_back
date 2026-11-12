package com.sajarok.domain.storycard.dto;

import com.sajarok.domain.storycard.entity.StoryCardType;

/**
 * 19번: 회고로 만든 카드 초안. {@code StoryCardService.saveRetrospectiveDraft}에 넘긴다.
 *
 * @param type SESSION / ASSIGNMENT (회고 종류와 같다)
 * @param fullMarkdown 노션용 회고록 전문 (# 제목 포함)
 * @param evidence 칸별 근거 JSON 문자열. 예: {"situation":[{"attachmentId":501,"quote":"..."}]}
 */
public record RetroCardDraft(
    Long userId,
    Long retrospectiveId,
    Long weekId,
    StoryCardType type,
    String title,
    String situation,
    String action,
    String reason,
    String result,
    String learned,
    String fullMarkdown,
    String evidence) {}
