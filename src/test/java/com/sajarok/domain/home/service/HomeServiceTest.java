package com.sajarok.domain.home.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.sajarok.domain.project.dto.HomeProjectTiles;
import com.sajarok.domain.project.service.ProjectService;
import com.sajarok.domain.retrospective.dto.HomeRetroSummary;
import com.sajarok.domain.retrospective.dto.RetrospectiveState;
import com.sajarok.domain.retrospective.service.RetrospectiveService;
import com.sajarok.domain.storycard.service.StoryCardService;
import com.sajarok.domain.user.entity.Part;
import com.sajarok.domain.user.entity.User;
import com.sajarok.domain.user.repository.UserRepository;
import com.sajarok.domain.week.service.WeekService;
import com.sajarok.domain.week.service.WeekService.CurrentWeek;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class HomeServiceTest {

  @Mock private UserRepository userRepository;
  @Mock private WeekService weekService;
  @Mock private RetrospectiveService retrospectiveService;
  @Mock private ProjectService projectService;
  @Mock private StoryCardService storyCardService;

  @InjectMocks private HomeService homeService;

  @Test
  void getHome_includesCompletedPresentationWeekAsProjectSticker() {
    Long userId = 1L;
    Long regularWeekId = 112L;
    Long presentationWeekId = 113L;
    Long currentWeekId = 114L;
    List<Long> completedWeekIds = List.of(regularWeekId, presentationWeekId);

    User user = mock(User.class);
    when(user.getName()).thenReturn("김사자");
    when(user.getPart()).thenReturn(Part.PLAN_DESIGN);
    when(userRepository.findById(userId)).thenReturn(Optional.of(user));

    when(weekService.findCurrentWeek(Part.PLAN_DESIGN))
        .thenReturn(
            new CurrentWeek(
                currentWeekId,
                14,
                "REGULAR_2",
                "14주차 세션",
                OffsetDateTime.parse("2026-06-03T23:59:00+09:00"),
                null));
    when(retrospectiveService.homeSummary(userId, currentWeekId))
        .thenReturn(
            new HomeRetroSummary(null, RetrospectiveState.NOT_STARTED, completedWeekIds, true));
    when(projectService.homeTiles(userId)).thenReturn(new HomeProjectTiles(List.of(), List.of()));
    when(storyCardService.findRecentRetrospectiveCards(userId)).thenReturn(List.of());
    when(weekService.findWeekNumbers(Set.of(regularWeekId, presentationWeekId)))
        .thenReturn(Map.of(regularWeekId, 12, presentationWeekId, 13));
    when(weekService.findPresentationPhases(completedWeekIds))
        .thenReturn(Map.of(presentationWeekId, "IDEATHON"));

    var stickers = homeService.getHome(userId).stickers();

    assertThat(stickers.totalCount()).isEqualTo(2);
    assertThat(stickers.recentWeekNumbers()).containsExactly(12);
    assertThat(stickers.projectStickers()).containsExactly("IDEATHON");
    assertThat(stickers.showThisWeekSlot()).isTrue();
  }
}
