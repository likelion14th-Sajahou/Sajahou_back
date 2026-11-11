package com.sajarok.domain.storycard.dto;

import com.sajarok.domain.storycard.entity.StoryCard;
import com.sajarok.domain.storycard.entity.StoryCardStatus;
import com.sajarok.domain.storycard.entity.StoryCardType;
import java.time.LocalDate;
import java.util.List;

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
    Object evidence,
    List<String> evidenceSummary,
    SourceRecord sourceRecord) {

  public record CompetencyView(Long id, String name) {}

  public record SourceRecord(Long recordId, LocalDate date) {}

  public static StoryCardDetailResponse of(
      StoryCard card, String projectName, LocalDate recordDate) {
    List<CompetencyView> competencies =
        card.getCompetencyIds().stream().sorted().map(id -> new CompetencyView(id, null)).toList();

    SourceRecord sourceRecord =
        card.getProjectRecordId() == null
            ? null
            : new SourceRecord(card.getProjectRecordId(), recordDate);

    List<String> evidenceSummary = recordDate == null ? List.of() : List.of(recordDate + " 기록");

    return new StoryCardDetailResponse(
        card.getId(),
        card.getType(),
        card.getStatus(),
        null, // TODO(주차 담당): weekId → weekNumber
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
        null, // TODO(19번): 근거 JSON
        evidenceSummary,
        sourceRecord);
  }
}
