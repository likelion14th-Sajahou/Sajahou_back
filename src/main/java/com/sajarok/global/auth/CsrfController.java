package com.sajarok.global.auth;

import com.sajarok.global.response.ApiResponse;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CsrfController {

  @GetMapping("/api/v1/auth/csrf")
  public ApiResponse<CsrfResponse> csrf(CsrfToken token) {
    return ApiResponse.ok(new CsrfResponse(token.getHeaderName(), token.getToken()));
  }

  public record CsrfResponse(String headerName, String token) {}
}
