package com.sajarok.domain.storycard.dto;

import com.fasterxml.jackson.annotation.JsonRawValue;
import com.sajarok.domain.storycard.entity.StoryCard;
import com.sajarok.domain.storycard.entity.StoryCardStatus;
import com.sajarok.domain.storycard.entity.StoryCardType;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public record StoryCardDetailResponse(
    Long cardId,
    StoryCardType type,
    StoryCardStatus status,
    Integer weekNumber,
    Long projectId,
    String projectName,
    String title,
    String situation,
    String action,
    String reason,
    String result,
    String learned,
    List<String> blanks,
    List<CompetencyView> competencies,
    String fullMarkdown,
    @JsonRawValue String evidence, // DB의 JSON 문자열을 그대로 JSON 객체로 내보낸다 (19번)
    List<String> evidenceSummary,
    SourceRecord sourceRecord) {

  public record CompetencyView(Long id, String name) {}

  public record SourceRecord(Long recordId, LocalDate date) {}

  /** 역량 이름 없이 만든다 (13·19번처럼 회고 화면 안의 카드). 역량은 id만, name은 null */
  public static StoryCardDetailResponse of(
      StoryCard card, String projectName, LocalDate recordDate) {
    return of(card, projectName, recordDate, Map.of(), null);
  }

  /**
   * 21~23번 카드 화면용: 역량 이름까지 채운다.
   *
   * @param competencyNames 역량 ID → 이름 (CompetencyService.names). 없는 ID는 name이 null
   * @param weekNumber 회고 카드의 주차 번호 (WeekService). 프로젝트 카드는 null
   */
  public static StoryCardDetailResponse of(
      StoryCard card,
      String projectName,
      LocalDate recordDate,
      Map<Long, String> competencyNames,
      Integer weekNumber) {
    List<CompetencyView> competencies =
        card.getCompetencyIds().stream()
            .sorted()
            .map(id -> new CompetencyView(id, competencyNames.get(id)))
            .toList();

    SourceRecord sourceRecord =
        card.getProjectRecordId() == null
            ? null
            : new SourceRecord(card.getProjectRecordId(), recordDate);

    List<String> evidenceSummary = recordDate == null ? List.of() : List.of(recordDate + " 기록");

    return new StoryCardDetailResponse(
        card.getId(),
        card.getType(),
        card.getStatus(),
        weekNumber,
        card.getProjectId(),
        projectName,
        card.getTitle(),
        card.getSituation(),
        card.getAction(),
        card.getReason(),
        card.getResult(),
        card.getLearned(),
        card.blanks(),
        competencies,
        card.getFullMarkdown(),
        card.getEvidence(),
        evidenceSummary,
        sourceRecord);
  }
}
