package com.sajarok.domain.user.controller;

import com.sajarok.domain.user.dto.UserProfileUpdateRequest;
import com.sajarok.domain.user.entity.User;
import com.sajarok.domain.user.service.UserProfileService;
import com.sajarok.global.auth.LoginSessionService;
import com.sajarok.global.response.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class UserMeController {

  private final UserProfileService userProfileService;
  private final LoginSessionService loginSessionService;

  @GetMapping("/api/v1/users/me")
  public ApiResponse<UserMeResponse> me(
      @AuthenticationPrincipal Long userId, HttpServletRequest request) {
    return toResponse(userProfileService.getProfile(userId), request);
  }

  @PatchMapping("/api/v1/users/me")
  public ApiResponse<UserMeResponse> updateMe(
      @AuthenticationPrincipal Long userId,
      @Valid @RequestBody UserProfileUpdateRequest body,
      HttpServletRequest request) {
    return toResponse(userProfileService.updateProfile(userId, body), request);
  }

  private ApiResponse<UserMeResponse> toResponse(User user, HttpServletRequest request) {
    return ApiResponse.ok(
        new UserMeResponse(
            user.getId(),
            user.getEmail(),
            user.getName(),
            user.getStudentNo(),
            user.getDepartment(),
            user.getCohort(),
            user.getPart().name(),
            loginSessionService.isDemo(request)));
  }

  public record UserMeResponse(
      Long id,
      String email,
      String name,
      String studentNo,
      String department,
      int cohort,
      String part,
      boolean isDemo) {}
}
