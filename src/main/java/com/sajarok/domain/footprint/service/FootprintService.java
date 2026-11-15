package com.sajarok.domain.footprint.service;

import com.sajarok.domain.footprint.dto.FootprintsResponse;
import com.sajarok.domain.footprint.dto.FootprintsResponse.Status;
import com.sajarok.domain.footprint.dto.FootprintsResponse.WeekItem;
import com.sajarok.domain.project.entity.ProjectCategory;
import com.sajarok.domain.project.service.ProjectService;
import com.sajarok.domain.retrospective.dto.RetrospectiveState;
import com.sajarok.domain.retrospective.service.RetrospectiveService;
import com.sajarok.domain.storycard.dto.CardSnapshot;
import com.sajarok.domain.storycard.entity.StoryCardType;
import com.sajarok.domain.storycard.service.StoryCardService;
import com.sajarok.domain.user.repository.UserRepository;
import com.sajarok.domain.week.service.WeekService;
import com.sajarok.global.exception.BusinessException;
import com.sajarok.global.exception.ErrorCode;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FootprintService {

  private final UserRepository userRepository;
  private final WeekService weekService;
  private final RetrospectiveService retrospectiveService;
  private final StoryCardService storyCardService;
  private final ProjectService projectService;

  public FootprintsResponse getFootprints(Long userId) {
    var user =
        userRepository
            .findById(userId)
            .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));

    int currentWeekNumber = weekService.findCurrentWeek(user.getPart()).weekNumber();

    Set<Long> completedWeekIds =
        retrospectiveService.footprints(userId).stream()
            .filter(
                week ->
                    week.sessionState() == RetrospectiveState.COMPLETED
                        || week.assignmentState() == RetrospectiveState.COMPLETED)
            .map(week -> week.weekId())
            .collect(Collectors.toSet());

    List<CardSnapshot> published = storyCardService.findPublished(userId);
    Map<Long, List<Long>> cardIdsByWeek =
        published.stream()
            .filter(card -> card.weekId() != null)
            .collect(
                Collectors.groupingBy(
                    CardSnapshot::weekId,
                    Collectors.mapping(CardSnapshot::cardId, Collectors.toList())));

    // 발표 주차에는 그 행사를 완료한 프로젝트의 카드도 그 주차 카드로 친다 (스티커와 같은 기준: 완료한 프로젝트만)
    Map<Long, ProjectCategory> completedProjects =
        projectService.findCompletedProjectCategories(userId);
    Map<String, List<Long>> projectCardIdsByEvent =
        published.stream()
            .filter(card -> card.type() == StoryCardType.PROJECT && card.projectId() != null)
            .filter(card -> completedProjects.containsKey(card.projectId()))
            .collect(
                Collectors.groupingBy(
                    card -> completedProjects.get(card.projectId()).name(),
                    Collectors.mapping(CardSnapshot::cardId, Collectors.toList())));

    Set<String> completedEvents =
        projectService.findCompletedCategories(userId).stream()
            .map(Enum::name)
            .collect(Collectors.toSet());

    List<WeekItem> weeks =
        weekService.findFootprintWeeks(user.getPart()).stream()
            .map(
                week -> {
                  Status status =
                      resolveStatus(
                          week.weekNumber(),
                          currentWeekNumber,
                          completedWeekIds.contains(week.weekId()));
                  return new WeekItem(
                      week.weekNumber(),
                      week.phase(),
                      status,
                      week.weekNumber() == currentWeekNumber,
                      week.isPresentationWeek(),
                      status == Status.UPCOMING && !week.isPresentationWeek()
                          ? null
                          : week.sessionTitle(),
                      cardIdsOf(week, cardIdsByWeek, projectCardIdsByEvent),
                      stickersOf(week, completedEvents));
                })
            .toList();

    return new FootprintsResponse(currentWeekNumber, weeks);
  }

  /** 그 주차 회고 카드, 발표 주차면 뒤에 그 행사를 완료한 프로젝트의 카드까지 */
  private static List<Long> cardIdsOf(
      WeekService.FootprintWeek week,
      Map<Long, List<Long>> cardIdsByWeek,
      Map<String, List<Long>> projectCardIdsByEvent) {
    List<Long> retroCardIds = cardIdsByWeek.getOrDefault(week.weekId(), List.of());
    String category = week.presentationCategory();
    if (!week.isPresentationWeek() || category == null) {
      return retroCardIds;
    }
    List<Long> projectCardIds = projectCardIdsByEvent.getOrDefault(category, List.of());
    return Stream.concat(retroCardIds.stream(), projectCardIds.stream()).toList();
  }

  private static List<String> stickersOf(
      WeekService.FootprintWeek week, Set<String> completedEvents) {
    String category = week.presentationCategory();
    return week.isPresentationWeek() && category != null && completedEvents.contains(category)
        ? List.of(category)
        : List.of();
  }

  private static Status resolveStatus(int weekNumber, int currentWeekNumber, boolean completed) {
    if (weekNumber > currentWeekNumber) {
      return Status.UPCOMING;
    }
    if (completed) {
      return Status.COMPLETED;
    }
    return weekNumber == currentWeekNumber ? Status.THIS_WEEK : Status.NO_RECORD;
  }
}
