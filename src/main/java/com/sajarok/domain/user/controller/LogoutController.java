package com.sajarok.domain.user.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.logout.CookieClearingLogoutHandler;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class LogoutController {

  private final SecurityContextLogoutHandler sessionLogout = new SecurityContextLogoutHandler();
  private final CookieClearingLogoutHandler cookieLogout =
      new CookieClearingLogoutHandler("JSESSIONID");

  @PostMapping("/api/v1/auth/logout")
  public ResponseEntity<Void> logout(
      HttpServletRequest request, HttpServletResponse response, Authentication authentication) {
    sessionLogout.logout(request, response, authentication);
    cookieLogout.logout(request, response, authentication);
    return ResponseEntity.noContent().build();
  }
}
