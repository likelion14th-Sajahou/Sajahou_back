package com.sajarok.domain.user.dto;

public record GoogleLoginResponse(
    boolean registered, UserSummary user, String signupToken, String email, String suggestedName) {

  public record UserSummary(Long id, String name, String part) {}
}
