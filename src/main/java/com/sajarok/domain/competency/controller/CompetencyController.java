package com.sajarok.domain.competency.controller;

import com.sajarok.domain.competency.entity.Competency;
import com.sajarok.domain.competency.service.CompetencyService;
import com.sajarok.domain.storycard.service.StoryCardService;
import com.sajarok.global.auth.CurrentUserProvider;
import com.sajarok.global.response.ApiResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class CompetencyController {

  private final CompetencyService competencyService;
  private final StoryCardService storyCardService;
  private final CurrentUserProvider currentUserProvider;

  @GetMapping("/api/v1/competencies")
  public ApiResponse<List<CompetencyResponse>> findAll() {
    Long userId = currentUserProvider.getUserId();
    var competencies = competencyService.findForUser(userId);
    var cardCounts = storyCardService.countByCompetency(userId);

    return ApiResponse.ok(
        competencies.stream()
            .map(
                competency ->
                    CompetencyResponse.from(
                        competency, cardCounts.getOrDefault(competency.getId(), 0L)))
            .toList());
  }

  public record CompetencyResponse(
      Long id, String part, String name, String description, long cardCount) {

    static CompetencyResponse from(Competency competency, long cardCount) {
      return new CompetencyResponse(
          competency.getId(),
          competency.getPart().name(),
          competency.getName(),
          competency.getDescription(),
          cardCount);
    }
  }
}
