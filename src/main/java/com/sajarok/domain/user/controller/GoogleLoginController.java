package com.sajarok.domain.user.controller;

import com.sajarok.domain.user.dto.GoogleLoginRequest;
import com.sajarok.domain.user.dto.GoogleLoginResponse;
import com.sajarok.domain.user.service.GoogleLoginService;
import com.sajarok.domain.user.service.SignupTokenService;
import com.sajarok.global.auth.LoginSessionService;
import com.sajarok.global.response.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.io.IOException;
import java.security.GeneralSecurityException;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class GoogleLoginController {

  private final GoogleLoginService googleLoginService;
  private final LoginSessionService loginSessionService;
  private final SignupTokenService signupTokenService;

  @PostMapping("/google")
  public ApiResponse<GoogleLoginResponse> login(
      @Valid @RequestBody GoogleLoginRequest body,
      HttpServletRequest request,
      HttpServletResponse response)
      throws GeneralSecurityException, IOException {

    var lookup = googleLoginService.lookup(body.idToken());

    if (lookup.user().isPresent()) {
      var user = lookup.user().orElseThrow();
      loginSessionService.login(user.id(), request, response);
      return ApiResponse.ok(new GoogleLoginResponse(true, user, null, null, null));
    }

    var identity = lookup.identity();
    String signupToken = signupTokenService.issue(identity, request);
    return ApiResponse.ok(
        new GoogleLoginResponse(
            false, null, signupToken, identity.email(), identity.suggestedName()));
  }
}
