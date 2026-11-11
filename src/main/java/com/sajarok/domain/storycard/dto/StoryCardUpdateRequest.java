package com.sajarok.domain.storycard.dto;

import jakarta.validation.constraints.Size;
import java.util.Set;

/**
 * 23번 카드 수정. null인 칸은 그대로, 빈 문자열은 그 칸을 비운다.
 *
 * <p>최대 길이는 DB 칸을 넘겨 500이 나지 않게 막는 용도다. 역량 개수(0~3, 1~3)는 엔티티가 COMPETENCY_LIMIT로 검사한다.
 */
public record StoryCardUpdateRequest(
    @Size(max = 200, message = "제목은 200자까지 작성 가능해요.") String title,
    @Size(max = 10000, message = "상황은 10,000자까지 작성 가능해요.") String situation,
    @Size(max = 10000, message = "행동은 10,000자까지 작성 가능해요.") String action,
    @Size(max = 10000, message = "이유는 10,000자까지 작성 가능해요.") String reason,
    @Size(max = 10000, message = "결과는 10,000자까지 작성 가능해요.") String result,
    @Size(max = 10000, message = "배운 점은 10,000자까지 작성 가능해요.") String learned,
    @Size(max = 100000, message = "전문은 100,000자까지 작성 가능해요.") String fullMarkdown,
    Set<Long> competencyIds) {}
