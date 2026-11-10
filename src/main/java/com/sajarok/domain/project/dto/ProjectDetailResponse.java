package com.sajarok.domain.project.dto;

import com.sajarok.domain.project.dto.ProjectRecordResponse.CompetencyTag;
import com.sajarok.domain.project.entity.Project;
import com.sajarok.domain.project.entity.ProjectRecord;
import com.sajarok.domain.project.entity.ProjectRole;
import com.sajarok.domain.project.entity.ProjectStatus;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

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

  private static final ZoneOffset KST = ZoneOffset.ofHours(9);

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

    // TODO(카드·역량·첨부 담당): findByRecordIds·names·findAll로 한 번에 가져와 채운다 (기록마다 부르지 않음, N+1 방지).
    public static RecordItem from(ProjectRecord record) {
      return new RecordItem(
          record.getId(),
          null,
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

  public static ProjectDetailResponse of(Project project, List<ProjectRecord> savedRecords) {
    List<RecordItem> records = savedRecords.stream().map(RecordItem::from).toList();
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
    return new Portfolio(project.getPortfolioMarkdown(), toKst(project.getPortfolioSavedAt()));
  }

  private static LocalDate toDate(LocalDateTime dateTime) {
    return dateTime == null ? null : dateTime.toLocalDate();
  }

  /**
   * DB 시각(DATETIME, 시간대 없음)을 한국 시각(+09:00)으로 바꾼다. DB 시각이 한국 시간이라고 가정한다(JVM 시간대는 팀과 정하는 중).
   *
   * <p>TODO(팀 논의): 다른 API도 쓰게 되면 global 공통 유틸로 옮긴다.
   */
  private static OffsetDateTime toKst(LocalDateTime dateTime) {
    return dateTime == null ? null : dateTime.atOffset(KST);
  }
}
