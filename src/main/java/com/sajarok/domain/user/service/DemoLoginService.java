package com.sajarok.domain.user.service;

import com.sajarok.domain.user.dto.GoogleLoginResponse.UserSummary;
import com.sajarok.domain.user.entity.Part;
import com.sajarok.domain.user.entity.User;
import com.sajarok.domain.user.repository.UserRepository;
import com.sajarok.global.exception.BusinessException;
import com.sajarok.global.exception.ErrorCode;
import java.time.LocalDateTime;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class DemoLoginService {

  private static final String TEMPORARY_PROVIDER_PREFIX = "demo-session:";

  private final UserRepository userRepository;
  private final boolean enabled;

  public DemoLoginService(
      UserRepository userRepository, @Value("${auth.demo.enabled}") boolean enabled) {
    this.userRepository = userRepository;
    this.enabled = enabled;
  }

  public UserSummary getOrCreateDemoUser(Long existingUserId) {
    if (!enabled) {
      throw new BusinessException(ErrorCode.NOT_FOUND);
    }

    User user =
        existingUserId == null
            ? createDemoUser()
            : userRepository
                .findById(existingUserId)
                .filter(DemoLoginService::isTemporaryDemo)
                .orElseGet(this::createDemoUser);

    return new UserSummary(user.getId(), user.getName(), user.getPart().name());
  }

  public static boolean isTemporaryDemo(User user) {
    return user.getProviderId().startsWith(TEMPORARY_PROVIDER_PREFIX);
  }

  private User createDemoUser() {
    String id = UUID.randomUUID().toString();
    return userRepository.save(
        User.register(
            TEMPORARY_PROVIDER_PREFIX + id,
            "demo-" + id + "@example.invalid",
            "김사자",
            "00000000",
            "경영학부",
            14,
            Part.PLAN_DESIGN,
            LocalDateTime.now()));
  }
}
