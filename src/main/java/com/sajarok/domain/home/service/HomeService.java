package com.sajarok.domain.home.service;

import com.sajarok.domain.home.dto.HomeResponse;
import com.sajarok.domain.home.dto.HomeResponse.RecentCardItem;
import com.sajarok.domain.home.dto.HomeResponse.SessionRetrospective;
import com.sajarok.domain.home.dto.HomeResponse.Stickers;
import com.sajarok.domain.home.dto.HomeResponse.UserSummary;
import com.sajarok.domain.project.service.ProjectService;
import com.sajarok.domain.retrospective.service.RetrospectiveService;
import com.sajarok.domain.storycard.service.StoryCardService;
import com.sajarok.domain.user.repository.UserRepository;
import com.sajarok.domain.week.service.WeekService;
import com.sajarok.global.exception.BusinessException;
import com.sajarok.global.exception.ErrorCode;
import java.util.Comparator;
import java.util.HashSet;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class HomeService {

  private final UserRepository userRepository;
  private final WeekService weekService;
  private final RetrospectiveService retrospectiveService;
  private final ProjectService projectService;
  private final StoryCardService storyCardService;

  public HomeResponse getHome(Long userId) {
    var user =
        userRepository
            .findById(userId)
            .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));

    var currentWeek = weekService.findCurrentWeek(user.getPart());
    var retrospective = retrospectiveService.homeSummary(userId, currentWeek.weekId());
    var projectTiles = projectService.homeTiles(userId);
    var cards = storyCardService.findRecentRetrospectiveCards(userId);

    var weekIds = new HashSet<>(retrospective.stickerWeekIds());
    cards.forEach(card -> weekIds.add(card.weekId()));
    weekIds.remove(null);
    var weekNumbers = weekService.findWeekNumbers(weekIds);

    var stickerWeekIds = retrospective.stickerWeekIds();
    var presentationPhases = weekService.findPresentationPhases(stickerWeekIds);

    var completedStickerWeekIds =
        stickerWeekIds.stream().filter(weekNumbers::containsKey).distinct().toList();

    var projectStickers =
        completedStickerWeekIds.stream()
            .filter(presentationPhases::containsKey)
            .sorted(Comparator.comparing(weekNumbers::get))
            .map(presentationPhases::get)
            .distinct()
            .toList();

    var recentWeekNumbers =
        completedStickerWeekIds.stream()
            .filter(id -> !presentationPhases.containsKey(id))
            .map(weekNumbers::get)
            .sorted(Comparator.reverseOrder())
            .limit(10)
            .toList();

    var recentCards =
        cards.stream()
            .map(
                card ->
                    new RecentCardItem(
                        card.cardId(),
                        card.type(),
                        card.weekId() == null ? null : weekNumbers.get(card.weekId()),
                        card.title()))
            .toList();

    return new HomeResponse(
        new UserSummary(user.getName(), user.getPart()),
        currentWeek,
        new SessionRetrospective(retrospective.sessionRetrospectiveId(), retrospective.state()),
        new Stickers(
            completedStickerWeekIds.size(),
            recentWeekNumbers,
            projectStickers,
            retrospective.showThisWeekSlot()),
        projectTiles.inProgressProjects(),
        projectTiles.doneProjects(),
        recentCards);
  }
}
