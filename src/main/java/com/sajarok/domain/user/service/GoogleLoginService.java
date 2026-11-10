package com.sajarok.domain.user.service;

import com.sajarok.domain.user.dto.GoogleLoginResponse.UserSummary;
import com.sajarok.domain.user.entity.User;
import com.sajarok.domain.user.repository.UserRepository;
import com.sajarok.domain.user.service.GoogleTokenVerifier.GoogleIdentity;
import com.sajarok.global.exception.BusinessException;
import com.sajarok.global.exception.ErrorCode;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class GoogleLoginService {

  private final GoogleTokenVerifier googleTokenVerifier;
  private final UserRepository userRepository;

  public LoginLookup lookup(String idToken) throws GeneralSecurityException, IOException {
    GoogleIdentity identity =
        googleTokenVerifier
            .verify(idToken)
            .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));

    Optional<User> user = userRepository.findByProviderId(identity.providerId());
    return new LoginLookup(
        identity,
        user.map(
            member -> new UserSummary(member.getId(), member.getName(), member.getPart().name())));
  }

  public record LoginLookup(GoogleIdentity identity, Optional<UserSummary> user) {}
}
