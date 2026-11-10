package com.sajarok.domain.user.controller;

import com.sajarok.domain.user.dto.GoogleLoginRequest;
import com.sajarok.domain.user.dto.GoogleLoginResponse;
import com.sajarok.domain.user.dto.GoogleLoginResponse.UserSummary;
import com.sajarok.domain.user.dto.SignupRequest;
import com.sajarok.domain.user.service.DemoLoginService;
import com.sajarok.domain.user.service.GoogleLoginService;
import com.sajarok.domain.user.service.SignupService;
import com.sajarok.domain.user.service.SignupTokenService;
import com.sajarok.global.auth.LoginSessionService;
import com.sajarok.global.response.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.io.IOException;
import java.security.GeneralSecurityException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
  private final SignupService signupService;
  private final DemoLoginService demoLoginService;

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

  @PostMapping("/signup")
  public ResponseEntity<ApiResponse<SignupResponse>> signup(
      @Valid @RequestBody SignupRequest body,
      HttpServletRequest request,
      HttpServletResponse response) {
    var identity = signupTokenService.verify(body.signupToken(), request);
    var user = signupService.register(body, identity);

    signupTokenService.clear(request);
    loginSessionService.login(user.id(), request, response);

    return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(new SignupResponse(user)));
  }

  public record SignupResponse(GoogleLoginResponse.UserSummary user) {}

  @PostMapping("/demo")
  public ApiResponse<DemoLoginResponse> demo(
      HttpServletRequest request, HttpServletResponse response) {
    UserSummary user = demoLoginService.getDemoUser();
    loginSessionService.login(user.id(), request, response);
    return ApiResponse.ok(new DemoLoginResponse(user));
  }

  public record DemoLoginResponse(UserSummary user) {}
}
