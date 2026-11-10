package com.sajarok.global.auth;

import com.sajarok.global.exception.BusinessException;
import com.sajarok.global.exception.ErrorCode;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class CurrentUserProvider {

  public Long getUserId() {
    var authentication = SecurityContextHolder.getContext().getAuthentication();

    if (authentication != null
        && authentication.isAuthenticated()
        && authentication.getPrincipal() instanceof Long userId) {
      return userId;
    }

    throw new BusinessException(ErrorCode.UNAUTHORIZED);
  }
}
