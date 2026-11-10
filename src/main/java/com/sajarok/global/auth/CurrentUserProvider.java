package com.sajarok.global.auth;

import org.springframework.stereotype.Component;

/**
 * 로그인한 회원 ID를 꺼내는 곳.
 *
 * <p>인증 담당이 실제 인증으로 바꿀 임시 클래스다. 지금은 인증이 없어서 항상 1번 회원을 돌려준다. 컨트롤러는 이 클래스로만 회원 ID를 꺼내므로, 인증이 생기면
 * {@link #getUserId()} 안만 바꾸면 된다.
 */
@Component
public class CurrentUserProvider {

  private static final Long TEMP_USER_ID = 1L;

  public Long getUserId() {
    return TEMP_USER_ID;
  }
}
