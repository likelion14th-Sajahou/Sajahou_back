package com.sajarok.domain.project.dto;

import com.sajarok.domain.project.dto.ProjectRecordResponse.CompetencyTag;
import com.sajarok.domain.project.entity.Project;
import com.sajarok.domain.project.entity.ProjectRecord;
import com.sajarok.domain.project.entity.ProjectRole;
import com.sajarok.domain.project.entity.ProjectStatus;
import com.sajarok.global.time.KstTime;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/** 26번 프로젝트 상세 응답. records는 저장된(SAVED) 기록만, 최신순. */
public record ProjectDetailResponse(
    Long projectId,
    String name,
    String subtitle,
    List<ProjectRole> roles,
    ProjectStatus status,
    LocalDate startedOn,
    int recordCount,
    LocalDate lastRecordDate,
    Portfolio portfolio,
    List<RecordItem> records) {

  /** 저장한 포트폴리오 (H-21). 없으면 portfolio 자체가 null. */
  public record Portfolio(String markdown, OffsetDateTime savedAt) {}

  /** 기록 한 개. cardId는 이 기록의 프로젝트 카드. */
  public record RecordItem(
      Long recordId,
      Long cardId,
      LocalDate date,
      boolean isAiOrganized,
      String memo,
      String did,
      String concern,
      String decision,
      String result,
      String nextStep,
      List<CompetencyTag> competencies,
      // TODO(첨부 담당): 첨부 응답 DTO가 생기면 그 타입으로 바꾼다. 지금은 빈 배열.
      List<Object> attachments) {

    // TODO(역량·첨부 담당): names·findAll로 한 번에 가져와 채운다 (기록마다 부르지 않음, N+1 방지).
    public static RecordItem of(ProjectRecord record, Long cardId) {
      return new RecordItem(
          record.getId(),
          cardId,
          toDate(record.getCreatedAt()),
          record.isAiOrganized(),
          record.getMemo(),
          record.getDid(),
          record.getConcern(),
          record.getDecision(),
          record.getResult(),
          record.getNextStep(),
          List.of(),
          List.of());
    }
  }

  /**
   * @param cardIds 기록 ID → 카드 ID (카드 담당 findByRecordIds로 한 번에 가져온 것). 없으면 cardId는 null.
   */
  public static ProjectDetailResponse of(
      Project project, List<ProjectRecord> savedRecords, Map<Long, Long> cardIds) {
    List<RecordItem> records =
        savedRecords.stream().map(r -> RecordItem.of(r, cardIds.get(r.getId()))).toList();
    return new ProjectDetailResponse(
        project.getId(),
        project.getName(),
        project.getSubtitle(),
        List.copyOf(project.getRoles()),
        project.getStatus(),
        toDate(project.getCreatedAt()),
        records.size(),
        records.isEmpty() ? null : records.get(0).date(),
        portfolio(project),
        records);
  }

  private static Portfolio portfolio(Project project) {
    if (project.getPortfolioMarkdown() == null) {
      return null;
    }
    return new Portfolio(
        project.getPortfolioMarkdown(), KstTime.toOffset(project.getPortfolioSavedAt()));
  }

  private static LocalDate toDate(LocalDateTime dateTime) {
    return dateTime == null ? null : dateTime.toLocalDate();
  }
}
