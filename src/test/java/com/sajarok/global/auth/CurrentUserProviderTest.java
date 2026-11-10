package com.sajarok.global.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sajarok.global.exception.BusinessException;
import com.sajarok.global.exception.ErrorCode;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;

/** 로그인한 회원 ID 꺼내기: 세션 로그인의 principal(Long)을 쓰고, 없으면 401. */
class CurrentUserProviderTest {

  private final CurrentUserProvider provider = new CurrentUserProvider();

  /** 로그인 정보가 다른 테스트에 남지 않게 비운다. */
  @AfterEach
  void clear() {
    SecurityContextHolder.clearContext();
  }

  @Test
  @DisplayName("로그인 정보(principal 7)가 있으면 7을 돌려준다")
  void loggedIn_returnsUserId() {
    SecurityContextHolder.getContext()
        .setAuthentication(UsernamePasswordAuthenticationToken.authenticated(7L, null, List.of()));

    assertThat(provider.getUserId()).isEqualTo(7L);
  }

  @Test
  @DisplayName("로그인 정보가 아예 없으면 UNAUTHORIZED")
  void noAuthentication_throwsUnauthorized() {
    assertThatThrownBy(provider::getUserId)
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.UNAUTHORIZED);
  }

  @Test
  @DisplayName("익명 사용자(principal이 \"anonymousUser\" 문자열)면 UNAUTHORIZED")
  void anonymous_throwsUnauthorized() {
    SecurityContextHolder.getContext()
        .setAuthentication(
            new AnonymousAuthenticationToken(
                "key", "anonymousUser", AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS")));

    assertThatThrownBy(provider::getUserId)
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.UNAUTHORIZED);
  }
}
