package com.sajarok.domain.home.controller;

import com.sajarok.domain.home.dto.HomeResponse;
import com.sajarok.domain.home.service.HomeService;
import com.sajarok.global.auth.CurrentUserProvider;
import com.sajarok.global.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class HomeController {

  private final HomeService homeService;
  private final CurrentUserProvider currentUserProvider;

  @GetMapping("/api/v1/home")
  public ApiResponse<HomeResponse> getHome() {
    return ApiResponse.ok(homeService.getHome(currentUserProvider.getUserId()));
  }
}
