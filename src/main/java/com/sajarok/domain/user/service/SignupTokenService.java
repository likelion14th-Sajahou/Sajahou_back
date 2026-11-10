package com.sajarok.domain.user.service;

import com.sajarok.domain.user.service.GoogleTokenVerifier.GoogleIdentity;
import com.sajarok.global.exception.BusinessException;
import com.sajarok.global.exception.ErrorCode;
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

  public GoogleIdentity verify(String token, HttpServletRequest request) {
    var session = request.getSession(false);

    if (session == null
        || !(session.getAttribute(SESSION_KEY) instanceof PendingSignup pending)
        || !pending.token().equals(token)
        || !Instant.now().isBefore(pending.expiresAt())) {
      throw new BusinessException(
          ErrorCode.UNAUTHORIZED, "가입 정보가 만료됐거나 유효하지 않습니다. 구글 로그인을 다시 해 주세요.");
    }

    return pending.identity();
  }

  public void clear(HttpServletRequest request) {
    var session = request.getSession(false);
    if (session != null) {
      session.removeAttribute(SESSION_KEY);
    }
  }
}
