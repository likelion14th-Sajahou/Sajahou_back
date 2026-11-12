package com.sajarok.domain.user.controller;

import com.sajarok.domain.user.service.UserWithdrawalService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class UserWithdrawalController {

  private final UserWithdrawalService userWithdrawalService;
  private final SecurityContextLogoutHandler sessionLogout = new SecurityContextLogoutHandler();

  @DeleteMapping("/api/v1/users/me")
  public ResponseEntity<Void> withdraw(
      @AuthenticationPrincipal Long userId,
      Authentication authentication,
      HttpServletRequest request,
      HttpServletResponse response) {
    userWithdrawalService.withdraw(userId);
    sessionLogout.logout(request, response, authentication);
    return ResponseEntity.noContent().build();
  }
}
