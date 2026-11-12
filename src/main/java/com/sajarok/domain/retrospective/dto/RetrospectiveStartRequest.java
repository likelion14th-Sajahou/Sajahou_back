package com.sajarok.domain.retrospective.dto;

import com.sajarok.domain.retrospective.entity.RetrospectiveType;
import jakarta.validation.constraints.NotNull;

public record RetrospectiveStartRequest(
    @NotNull(message = "주차를 골라 주세요.") Long weekId,
    @NotNull(message = "회고 종류를 골라 주세요.") RetrospectiveType type,
    Boolean rested) {
  public boolean wantsRest() {
    return Boolean.TRUE.equals(rested);
  }
}
