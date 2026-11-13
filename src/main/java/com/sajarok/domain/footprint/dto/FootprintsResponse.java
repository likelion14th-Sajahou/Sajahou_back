package com.sajarok.domain.footprint.dto;

import java.util.List;

public record FootprintsResponse(int currentWeekNumber, List<WeekItem> weeks) {

  public enum Status {
    COMPLETED,
    THIS_WEEK,
    UPCOMING,
    NO_RECORD
  }

  public record WeekItem(
      int weekNumber,
      String phase,
      Status status,
      boolean isCurrent,
      boolean isPresentationWeek,
      String sessionTitle,
      List<Long> cardIds,
      List<String> stickers) {}
}
