package com.sajarok.domain.footprint.service;

import com.sajarok.domain.footprint.dto.FootprintsResponse;
import com.sajarok.domain.footprint.dto.FootprintsResponse.Status;
import com.sajarok.domain.footprint.dto.FootprintsResponse.WeekItem;
import com.sajarok.domain.retrospective.dto.RetrospectiveState;
import com.sajarok.domain.retrospective.service.RetrospectiveService;
import com.sajarok.domain.storycard.dto.CardSnapshot;
import com.sajarok.domain.storycard.service.StoryCardService;
import com.sajarok.domain.user.repository.UserRepository;
import com.sajarok.domain.week.service.WeekService;
import com.sajarok.global.exception.BusinessException;
import com.sajarok.global.exception.ErrorCode;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
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

    Map<Long, List<Long>> cardIdsByWeek =
        storyCardService.findPublished(userId).stream()
            .filter(card -> card.weekId() != null)
            .collect(
                Collectors.groupingBy(
                    CardSnapshot::weekId,
                    Collectors.mapping(CardSnapshot::cardId, Collectors.toList())));

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
                      status == Status.UPCOMING ? null : week.sessionTitle(),
                      cardIdsByWeek.getOrDefault(week.weekId(), List.of()));
                })
            .toList();

    return new FootprintsResponse(currentWeekNumber, weeks);
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
