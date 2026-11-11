package com.sajarok.domain.retrospective.dto;

import jakarta.validation.constraints.NotNull;

/** 15번 휴회 표시·해제 */
public record RetrospectiveRestRequest(@NotNull(message = "휴회 여부를 보내 주세요.") Boolean rested) {}
