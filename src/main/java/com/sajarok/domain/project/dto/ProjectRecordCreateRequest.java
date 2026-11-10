package com.sajarok.domain.project.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * 27번 기록 추가 요청.
 *
 * <p>aiOrganize가 true면 DRAFT('AI와 정리하기'), false면 바로 SAVED('이대로 저장'). 칸마다 10,000자 상한은 TEXT 컬럼 보호용이다.
 * competencyIds는 SAVED일 때만 쓰고, DRAFT일 때는 무시한다 (태그는 28번 저장 때 받음, H-13·H-14).
 */
public record ProjectRecordCreateRequest(
    @NotNull(message = "AI와 정리할지 골라 주세요.") Boolean aiOrganize,
    @Size(max = 10000, message = "메모는 10,000자까지 쓸 수 있어요.") String memo,
    @Size(max = 10000, message = "한 일은 10,000자까지 쓸 수 있어요.") String did,
    @Size(max = 10000, message = "고민·해결은 10,000자까지 쓸 수 있어요.") String concern,
    @Size(max = 10000, message = "선택과 이유는 10,000자까지 쓸 수 있어요.") String decision,
    @Size(max = 10000, message = "결과·반응은 10,000자까지 쓸 수 있어요.") String result,
    @Size(max = 10000, message = "다음은 10,000자까지 쓸 수 있어요.") String nextStep,
    List<@NotNull Long> attachmentIds,
    List<@NotNull Long> competencyIds) {

  public boolean hasAttachments() {
    return attachmentIds != null && !attachmentIds.isEmpty();
  }
}
