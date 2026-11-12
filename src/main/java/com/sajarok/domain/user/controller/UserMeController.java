package com.sajarok.domain.user.controller;

import com.sajarok.domain.user.repository.UserRepository;
import com.sajarok.global.auth.LoginSessionService;
import com.sajarok.global.exception.BusinessException;
import com.sajarok.global.exception.ErrorCode;
import com.sajarok.global.response.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class UserMeController {

  private final UserRepository userRepository;
  private final LoginSessionService loginSessionService;

  @GetMapping("/api/v1/users/me")
  public ApiResponse<UserMeResponse> me(
      @AuthenticationPrincipal Long userId, HttpServletRequest request) {
    var user =
        userRepository
            .findById(userId)
            .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));

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
