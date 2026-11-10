package com.sajarok.domain.user.service;

import com.sajarok.domain.user.entity.Part;
import com.sajarok.domain.user.entity.User;
import com.sajarok.domain.user.repository.UserRepository;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "auth.demo.enabled", havingValue = "true")
public class DemoAccountInitializer implements ApplicationRunner {

  private final UserRepository userRepository;

  @Override
  public void run(ApplicationArguments args) {
    if (userRepository.findByProviderId(DemoLoginService.DEMO_PROVIDER_ID).isPresent()) {
      return;
    }

    userRepository.save(
        User.register(
            DemoLoginService.DEMO_PROVIDER_ID,
            "demo-sajarok@example.invalid",
            "김사자",
            "00000000",
            "경영학부",
            14,
            Part.PLAN_DESIGN,
            LocalDateTime.now()));
  }
}
