package com.sajarok.domain.user.service;

import com.sajarok.domain.user.repository.UserRepository;
import jakarta.servlet.http.HttpSession;
import jakarta.servlet.http.HttpSessionEvent;
import jakarta.servlet.http.HttpSessionListener;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.servlet.ServletListenerRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;

@Slf4j
@Configuration(proxyBeanMethods = false)
@RequiredArgsConstructor
public class DemoSessionCleanup {

  private static final String DEMO_USER_ID = "SAJAROK_TEMPORARY_DEMO_USER_ID";

  private final UserRepository userRepository;
  private final UserWithdrawalService withdrawalService;

  public static Long getUserId(HttpSession session) {
    return (Long) session.getAttribute(DEMO_USER_ID);
  }

  public static void track(HttpSession session, Long userId) {
    session.setAttribute(DEMO_USER_ID, userId);
  }

  @Bean
  ServletListenerRegistrationBean<HttpSessionListener> demoSessionListener(
      TaskExecutor applicationTaskExecutor) {
    return new ServletListenerRegistrationBean<>(
        new HttpSessionListener() {
          @Override
          public void sessionDestroyed(HttpSessionEvent event) {
            Long userId = getUserId(event.getSession());
            if (userId != null) {
              try {
                applicationTaskExecutor.execute(() -> deleteTemporaryDemo(userId));
              } catch (RuntimeException e) {
                log.warn("임시 데모 계정 정리 작업 제출 실패 userId={}", userId, e);
              }
            }
          }
        });
  }

  private void deleteTemporaryDemo(Long userId) {
    try {
      userRepository
          .findById(userId)
          .filter(DemoLoginService::isTemporaryDemo)
          .ifPresent(user -> withdrawalService.withdraw(user.getId()));
    } catch (RuntimeException e) {
      log.warn("임시 데모 계정 정리 실패 userId={}", userId, e);
    }
  }
}
