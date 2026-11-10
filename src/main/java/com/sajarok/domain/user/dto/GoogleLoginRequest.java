package com.sajarok.domain.user.dto;

import jakarta.validation.constraints.NotBlank;

public record GoogleLoginRequest(@NotBlank(message = "구글 ID 토큰을 입력해 주세요.") String idToken) {}
