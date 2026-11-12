package com.sajarok.global.auth;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

class LoginSessionServiceTest {

  private final LoginSessionService loginSessionService = new LoginSessionService();

  @AfterEach
  void clearSecurityContext() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void normalLoginClearsDemoFlagInExistingSession() {
    var demoRequest = new MockHttpServletRequest();
    loginSessionService.login(1L, demoRequest, new MockHttpServletResponse(), true);
    assertThat(loginSessionService.isDemo(demoRequest)).isTrue();

    var normalRequest = new MockHttpServletRequest();
    normalRequest.setSession(demoRequest.getSession(false));
    loginSessionService.login(2L, normalRequest, new MockHttpServletResponse());

    assertThat(loginSessionService.isDemo(normalRequest)).isFalse();
  }
}
