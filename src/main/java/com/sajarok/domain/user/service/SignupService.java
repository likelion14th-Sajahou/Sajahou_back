package com.sajarok.domain.user.service;

import com.sajarok.domain.user.dto.GoogleLoginResponse.UserSummary;
import com.sajarok.domain.user.dto.SignupRequest;
import com.sajarok.domain.user.entity.User;
import com.sajarok.domain.user.repository.UserRepository;
import com.sajarok.domain.user.service.GoogleTokenVerifier.GoogleIdentity;
import com.sajarok.global.exception.BusinessException;
import com.sajarok.global.exception.ErrorCode;
import java.time.LocalDateTime;
import java.time.ZoneId;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SignupService {

  private final UserRepository userRepository;

  @Transactional
  public UserSummary register(SignupRequest body, GoogleIdentity identity) {
    if (userRepository.findByProviderId(identity.providerId()).isPresent()) {
      throw new BusinessException(ErrorCode.INVALID_STATE, "이미 가입한 계정입니다. 다시 로그인해 주세요.");
    }

    var user =
        userRepository.save(
            User.register(
                identity.providerId(),
                identity.email(),
                body.name(),
                body.studentNo(),
                body.department(),
                body.cohort(),
                body.part(),
                LocalDateTime.now(ZoneId.of("Asia/Seoul"))));

    return new UserSummary(user.getId(), user.getName(), user.getPart().name());
  }
}
