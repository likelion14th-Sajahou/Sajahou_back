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
import com.sajarok.domain.storycard.dto.CardSnapshot;
import com.sajarok.domain.storycard.entity.StoryCardType;
import com.sajarok.domain.storycard.service.StoryCardService;
import com.sajarok.domain.user.entity.Part;
import com.sajarok.domain.user.entity.User;
import com.sajarok.domain.user.repository.UserRepository;
import com.sajarok.domain.week.service.WeekService;
import com.sajarok.domain.week.service.WeekService.CurrentWeek;
import com.sajarok.domain.week.service.WeekService.FootprintWeek;
import java.util.List;
import java.util.Map;
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
                new FootprintWeek(27L, 27, "HACKATHON", true, "전체 회고", "HACKATHON"),
                new FootprintWeek(28L, 28, "REGULAR_3", false, "선배와의 만남", null),
                new FootprintWeek(37L, 37, "JUNGKATHON", false, "테스트코드", null),
                new FootprintWeek(38L, 38, "JUNGKATHON", true, "중커톤 발표·회고", "JUNGKATHON")));
    when(retrospectiveService.footprints(USER_ID)).thenReturn(List.of());
    when(storyCardService.findPublished(USER_ID)).thenReturn(List.of());
  }

  @Test
  void addsStickerOnlyToMatchingPresentationWeekEvenWhenUpcoming() {
    when(projectService.findCompletedCategories(USER_ID))
        .thenReturn(Set.of(ProjectCategory.HACKATHON, ProjectCategory.JUNGKATHON));

    var weeks = service.getFootprints(USER_ID).weeks();

    assertThat(weeks)
        .extracting(WeekItem::stickers)
        .containsExactly(List.of("HACKATHON"), List.of(), List.of(), List.of("JUNGKATHON"));
    assertThat(weeks.get(1).phase()).isEqualTo("REGULAR_3");
    assertThat(weeks.get(3).status()).isEqualTo(Status.UPCOMING);
    assertThat(weeks.get(3).sessionTitle()).isEqualTo("중커톤 발표·회고");
    verify(projectService).findCompletedCategories(USER_ID);
  }

  private static CardSnapshot card(long cardId, StoryCardType type, Long weekId, Long projectId) {
    return new CardSnapshot(
        cardId, type, weekId, projectId, "카드", null, null, null, null, null, Set.of());
  }

  @Test
  void presentationWeekCardIdsIncludeCompletedProjectCardsOfThatEvent() {
    when(projectService.findCompletedCategories(USER_ID))
        .thenReturn(Set.of(ProjectCategory.HACKATHON, ProjectCategory.JUNGKATHON));
    when(projectService.findCompletedProjectCategories(USER_ID))
        .thenReturn(Map.of(3L, ProjectCategory.HACKATHON, 5L, ProjectCategory.JUNGKATHON));
    when(storyCardService.findPublished(USER_ID))
        .thenReturn(
            List.of(
                card(301L, StoryCardType.SESSION, 27L, null), // 27주차 회고 카드
                card(412L, StoryCardType.PROJECT, null, 3L), // 완료한 해커톤 프로젝트
                card(413L, StoryCardType.PROJECT, null, 4L), // 진행 중인 프로젝트 → 안 들어감
                card(414L, StoryCardType.PROJECT, null, 5L), // 완료한 중커톤 프로젝트
                card(302L, StoryCardType.SESSION, 28L, null))); // 발표 주차가 아닌 28주차

    var weeks = service.getFootprints(USER_ID).weeks();

    assertThat(weeks)
        .extracting(WeekItem::cardIds)
        .containsExactly(List.of(301L, 412L), List.of(302L), List.of(), List.of(414L));
  }

  @Test
  void returnsEmptyStickerListsWhenNoEventProjectIsCompleted() {
    when(projectService.findCompletedCategories(USER_ID)).thenReturn(Set.of());

    assertThat(service.getFootprints(USER_ID).weeks())
        .allSatisfy(week -> assertThat(week.stickers()).isNotNull().isEmpty());
  }
}
