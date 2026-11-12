package com.sajarok.domain.user.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UserProfileUpdateRequest(
    @Size(max = 50) @Pattern(regexp = "(?sU).*\\S.*", message = "이름을 입력해 주세요.") String name,
    @Pattern(regexp = "[0-9]{8}", message = "학번은 숫자 8자리로 입력해 주세요.") String studentNo,
    @Size(max = 50) @Pattern(regexp = "(?sU).*\\S.*", message = "학과를 입력해 주세요.") String department,
    @Min(12) @Max(14) Integer cohort) {}
