package com.sajarok.domain.footprint.controller;

import com.sajarok.domain.footprint.dto.FootprintsResponse;
import com.sajarok.domain.footprint.service.FootprintService;
import com.sajarok.global.auth.CurrentUserProvider;
import com.sajarok.global.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class FootprintController {

  private final FootprintService footprintService;
  private final CurrentUserProvider currentUserProvider;

  @GetMapping("/api/v1/footprints")
  public ApiResponse<FootprintsResponse> getFootprints() {
    return ApiResponse.ok(footprintService.getFootprints(currentUserProvider.getUserId()));
  }
}
