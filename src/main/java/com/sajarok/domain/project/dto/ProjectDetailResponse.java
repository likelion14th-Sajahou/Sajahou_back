package com.sajarok.domain.project.dto;

import com.sajarok.domain.attachment.dto.AttachmentView;
import com.sajarok.domain.project.dto.ProjectRecordResponse.CompetencyTag;
import com.sajarok.domain.project.entity.Project;
import com.sajarok.domain.project.entity.ProjectCategory;
import com.sajarok.domain.project.entity.ProjectRecord;
import com.sajarok.domain.project.entity.ProjectRole;
import com.sajarok.domain.project.entity.ProjectStatus;
import com.sajarok.domain.storycard.dto.RecordCardInfo;
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
    ProjectCategory category,
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
      List<AttachmentView> attachments) {

    public static RecordItem of(
        ProjectRecord record,
        Long cardId,
        List<CompetencyTag> competencies,
        List<AttachmentView> attachments) {
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
          competencies,
          attachments);
    }
  }

  /**
   * @param cards 기록 ID → 카드 정보. 카드가 없으면 응답의 cardId는 null.
   * @param competencyNames 역량 ID → 태그 이름. 프로젝트의 전체 기록에서 사용하는 이름을 일괄 조회한 결과.
   * @param attachments 기록 ID → 첨부 (findAll로 한 번에 가져온 것). 없으면 빈 배열.
   */
  public static ProjectDetailResponse of(
      Project project,
      List<ProjectRecord> savedRecords,
      Map<Long, RecordCardInfo> cards,
      Map<Long, String> competencyNames,
      Map<Long, List<AttachmentView>> attachments) {
    List<RecordItem> records =
        savedRecords.stream()
            .map(
                record -> {
                  Long recordId = record.getId();
                  RecordCardInfo card = cards.get(recordId);
                  List<CompetencyTag> competencies =
                      card == null
                          ? List.of()
                          : card.competencyIds().stream()
                              .map(id -> new CompetencyTag(id, competencyNames.get(id)))
                              .toList();
                  return RecordItem.of(
                      record,
                      card == null ? null : card.cardId(),
                      competencies,
                      attachments.getOrDefault(recordId, List.of()));
                })
            .toList();
    return new ProjectDetailResponse(
        project.getId(),
        project.getName(),
        project.getSubtitle(),
        project.getCategory(),
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
