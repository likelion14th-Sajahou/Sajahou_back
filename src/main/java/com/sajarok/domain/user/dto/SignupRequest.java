package com.sajarok.domain.user.dto;

import com.sajarok.domain.user.entity.Part;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record SignupRequest(
    @NotBlank String signupToken,
    @NotBlank @Size(max = 50) String name,
    @NotBlank @Pattern(regexp = "[0-9]{8}", message = "학번은 숫자 8자리로 입력해 주세요.") String studentNo,
    @NotBlank @Size(max = 50) String department,
    @NotNull @Min(12) @Max(14) Integer cohort,
    @NotNull Part part,
    @NotNull @AssertTrue(message = "개인정보 처리에 동의해 주세요.") Boolean privacyAgreed) {}
