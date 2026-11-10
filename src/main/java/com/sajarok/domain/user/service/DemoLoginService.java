package com.sajarok.domain.user.service;

import com.sajarok.domain.user.dto.GoogleLoginResponse.UserSummary;
import com.sajarok.domain.user.repository.UserRepository;
import com.sajarok.global.exception.BusinessException;
import com.sajarok.global.exception.ErrorCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class DemoLoginService {

  private static final String DEMO_PROVIDER_ID = "demo-sajarok";

  private final UserRepository userRepository;
  private final boolean enabled;

  public DemoLoginService(
      UserRepository userRepository, @Value("${auth.demo.enabled}") boolean enabled) {
    this.userRepository = userRepository;
    this.enabled = enabled;
  }

  public UserSummary getDemoUser() {
    if (!enabled) {
      throw new BusinessException(ErrorCode.NOT_FOUND);
    }

    var user =
        userRepository
            .findByProviderId(DEMO_PROVIDER_ID)
            .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));

    return new UserSummary(user.getId(), user.getName(), user.getPart().name());
  }
}
