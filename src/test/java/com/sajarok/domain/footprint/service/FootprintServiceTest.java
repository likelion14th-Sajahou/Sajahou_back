package com.sajarok.domain.footprint.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sajarok.domain.footprint.dto.FootprintsResponse.Status;
import com.sajarok.domain.footprint.dto.FootprintsResponse.WeekItem;
import com.sajarok.domain.project.entity.ProjectCategory;
import com.sajarok.domain.project.service.ProjectService;
import com.sajarok.domain.retrospective.service.RetrospectiveService;
import com.sajarok.domain.storycard.service.StoryCardService;
import com.sajarok.domain.user.entity.Part;
import com.sajarok.domain.user.entity.User;
import com.sajarok.domain.user.repository.UserRepository;
import com.sajarok.domain.week.service.WeekService;
import com.sajarok.domain.week.service.WeekService.CurrentWeek;
import com.sajarok.domain.week.service.WeekService.FootprintWeek;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class FootprintServiceTest {

  private static final Long USER_ID = 1L;

  private final UserRepository userRepository = mock(UserRepository.class);
  private final WeekService weekService = mock(WeekService.class);
  private final RetrospectiveService retrospectiveService = mock(RetrospectiveService.class);
  private final StoryCardService storyCardService = mock(StoryCardService.class);
  private final ProjectService projectService = mock(ProjectService.class);
  private final FootprintService service =
      new FootprintService(
          userRepository, weekService, retrospectiveService, storyCardService, projectService);

  @BeforeEach
  void setUp() {
    User user = mock(User.class);
    when(user.getPart()).thenReturn(Part.BACKEND);
    when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
    when(weekService.findCurrentWeek(Part.BACKEND))
        .thenReturn(new CurrentWeek(37L, 37, "JUNGKATHON", null, null, null));
    when(weekService.findFootprintWeeks(Part.BACKEND))
        .thenReturn(
            List.of(
                new FootprintWeek(27L, 27, "HACKATHON", true, "전체 회고"),
                new FootprintWeek(37L, 37, "JUNGKATHON", false, "테스트코드"),
                new FootprintWeek(38L, 38, "JUNGKATHON", true, "중커톤 최종 발표")));
    when(retrospectiveService.footprints(USER_ID)).thenReturn(List.of());
    when(storyCardService.findPublished(USER_ID)).thenReturn(List.of());
  }

  @Test
  void addsStickerOnlyToMatchingPresentationWeekEvenWhenUpcoming() {
    when(projectService.findCompletedCategories(USER_ID))
        .thenReturn(Set.of(ProjectCategory.JUNGKATHON));

    var weeks = service.getFootprints(USER_ID).weeks();

    assertThat(weeks)
        .extracting(WeekItem::stickers)
        .containsExactly(List.of(), List.of(), List.of("JUNGKATHON"));
    assertThat(weeks.get(2).status()).isEqualTo(Status.UPCOMING);
    verify(projectService).findCompletedCategories(USER_ID);
  }

  @Test
  void returnsEmptyStickerListsWhenNoEventProjectIsCompleted() {
    when(projectService.findCompletedCategories(USER_ID)).thenReturn(Set.of());

    assertThat(service.getFootprints(USER_ID).weeks())
        .allSatisfy(week -> assertThat(week.stickers()).isNotNull().isEmpty());
  }
}
