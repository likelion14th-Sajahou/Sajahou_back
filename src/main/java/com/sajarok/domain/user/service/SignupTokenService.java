package com.sajarok.domain.user.service;

import com.sajarok.domain.user.service.GoogleTokenVerifier.GoogleIdentity;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class SignupTokenService {

  private static final String SESSION_KEY = SignupTokenService.class.getName() + ".pending";
  private static final Duration TOKEN_LIFETIME = Duration.ofMinutes(10);

  public String issue(GoogleIdentity identity, HttpServletRequest request) {
    String token = UUID.randomUUID().toString();
    Instant expiresAt = Instant.now().plus(TOKEN_LIFETIME);

    request.getSession().setAttribute(SESSION_KEY, new PendingSignup(token, identity, expiresAt));
    return token;
  }

  private record PendingSignup(String token, GoogleIdentity identity, Instant expiresAt) {}
}
