package com.sajarok.global.auth;

import com.sajarok.global.exception.BusinessException;
import com.sajarok.global.exception.ErrorCode;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * 로그인한 회원 ID를 꺼내는 곳. 컨트롤러는 이 클래스로만 회원 ID를 꺼낸다.
 *
 * <p>세션 로그인({@link LoginSessionService#login})이 principal에 회원 ID(Long)를 넣으므로 그 값을 꺼낸다.
 */
@Component
public class CurrentUserProvider {

  /**
   * 로그인한 회원 ID. 로그인 정보가 없거나 익명 사용자면 UNAUTHORIZED.
   *
   * <p>보안 필터가 먼저 막지만, 필터 밖에서 불려도 다른 회원으로 넘어가지 않게 여기서도 막는다.
   */
  public Long getUserId() {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth == null || !auth.isAuthenticated() || !(auth.getPrincipal() instanceof Long userId)) {
      throw new BusinessException(ErrorCode.UNAUTHORIZED);
    }
    return userId;
  }
}
