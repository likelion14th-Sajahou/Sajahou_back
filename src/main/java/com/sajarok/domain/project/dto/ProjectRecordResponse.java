package com.sajarok.domain.project.dto;

import com.sajarok.domain.project.entity.ProjectRecord;
import com.sajarok.domain.project.entity.ProjectRecordStatus;
import java.time.LocalDate;
import java.util.List;

/** 27·28번 기록 응답. DRAFT면 cardId는 null. */
public record ProjectRecordResponse(
    Long recordId,
    ProjectRecordStatus status,
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

  /** 역량 태그 한 개. */
  public record CompetencyTag(Long id, String name) {}

  public static ProjectRecordResponse of(
      ProjectRecord record,
      Long cardId,
      List<CompetencyTag> competencies,
      List<Object> attachments) {
    return new ProjectRecordResponse(
        record.getId(),
        record.getStatus(),
        cardId,
        record.getCreatedAt() == null ? null : record.getCreatedAt().toLocalDate(),
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
