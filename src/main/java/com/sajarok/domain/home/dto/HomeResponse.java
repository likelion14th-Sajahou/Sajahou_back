package com.sajarok.domain.home.dto;

import com.sajarok.domain.project.dto.HomeProjectTiles.DoneTile;
import com.sajarok.domain.project.dto.HomeProjectTiles.InProgressTile;
import com.sajarok.domain.retrospective.dto.RetrospectiveState;
import com.sajarok.domain.storycard.entity.StoryCardType;
import com.sajarok.domain.user.entity.Part;
import com.sajarok.domain.week.service.WeekService.CurrentWeek;
import java.util.List;

public record HomeResponse(
    UserSummary user,
    CurrentWeek currentWeek,
    SessionRetrospective sessionRetrospective,
    Stickers stickers,
    List<InProgressTile> inProgressProjects,
    List<DoneTile> doneProjects,
    List<RecentCardItem> recentCards) {

  public record UserSummary(String name, Part part) {}

  public record SessionRetrospective(Long id, RetrospectiveState state) {}

  public record Stickers(
      int totalCount,
      List<Integer> recentWeekNumbers,
      List<Object> projectStickers,
      boolean showThisWeekSlot) {}

  public record RecentCardItem(Long cardId, StoryCardType type, Integer weekNumber, String title) {}
}
