package com.sajarok.domain.project.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * 28번 기록 수정 요청. 보낸 필드만 바뀐다.
 *
 * <p>글자 칸(memo 포함): null이거나 안 보내면 그대로, ""이면 비운다. attachmentIds·competencyIds: 안 보내면 그대로, 보내면 통째로
 * 바꾼다. DRAFT 기록에 competencyIds를 보내면 '기록 저장'(SAVED + 카드 생성, H-14).
 */
public record ProjectRecordUpdateRequest(
    @Size(max = 10000, message = "메모는 10,000자까지 쓸 수 있어요.") String memo,
    @Size(max = 10000, message = "한 일은 10,000자까지 쓸 수 있어요.") String did,
    @Size(max = 10000, message = "고민·해결은 10,000자까지 쓸 수 있어요.") String concern,
    @Size(max = 10000, message = "선택과 이유는 10,000자까지 쓸 수 있어요.") String decision,
    @Size(max = 10000, message = "결과·반응은 10,000자까지 쓸 수 있어요.") String result,
    @Size(max = 10000, message = "다음은 10,000자까지 쓸 수 있어요.") String nextStep,
    Boolean isAiOrganized,
    List<@NotNull Long> attachmentIds,
    List<@NotNull Long> competencyIds) {}
