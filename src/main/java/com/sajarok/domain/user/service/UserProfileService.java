package com.sajarok.domain.user.service;

import com.sajarok.domain.user.dto.UserProfileUpdateRequest;
import com.sajarok.domain.user.entity.User;
import com.sajarok.domain.user.repository.UserRepository;
import com.sajarok.global.exception.BusinessException;
import com.sajarok.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserProfileService {

  private final UserRepository userRepository;

  public User getProfile(Long userId) {
    return userRepository
        .findById(userId)
        .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
  }

  @Transactional
  public User updateProfile(Long userId, UserProfileUpdateRequest request) {
    User user = getProfile(userId);
    user.updateProfile(request.name(), request.studentNo(), request.department(), request.cohort());
    return user;
  }
}
