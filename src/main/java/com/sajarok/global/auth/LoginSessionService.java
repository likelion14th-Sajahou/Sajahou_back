package com.sajarok.global.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.stereotype.Service;

@Service
public class LoginSessionService {

  private final HttpSessionSecurityContextRepository contextRepository =
      new HttpSessionSecurityContextRepository();

  private static final String DEMO_SESSION_ATTRIBUTE = "SAJAROK_DEMO_SESSION";

  public void login(Long userId, HttpServletRequest request, HttpServletResponse response) {
    login(userId, request, response, false);
  }

  public void login(
      Long userId, HttpServletRequest request, HttpServletResponse response, boolean isDemo) {
    if (request.getSession(false) != null) {
      request.changeSessionId();
    }

    var authentication = UsernamePasswordAuthenticationToken.authenticated(userId, null, List.of());
    var context = SecurityContextHolder.createEmptyContext();
    context.setAuthentication(authentication);
    SecurityContextHolder.setContext(context);
    contextRepository.saveContext(context, request, response);

    request.getSession().setAttribute(DEMO_SESSION_ATTRIBUTE, isDemo);
  }

  public boolean isDemo(HttpServletRequest request) {
    var session = request.getSession(false);
    return session != null && Boolean.TRUE.equals(session.getAttribute(DEMO_SESSION_ATTRIBUTE));
  }
}
