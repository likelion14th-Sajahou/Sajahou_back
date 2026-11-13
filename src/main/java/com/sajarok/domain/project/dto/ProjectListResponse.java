package com.sajarok.domain.project.dto;

import com.sajarok.domain.project.entity.ProjectCategory;
import com.sajarok.domain.project.entity.ProjectRole;
import java.time.LocalDate;
import java.util.List;

/** 43번 프로젝트 목록 응답. */
public record ProjectListResponse(
    List<ProjectSummary> inProgressProjects, List<ProjectSummary> doneProjects) {

  /**
   * @param category 행사 종류 (IDEATHON·HACKATHON·JUNGKATHON)
   * @param recordCount 저장된(SAVED) 기록 수
   * @param lastRecordDate 마지막 기록 날짜. 기록이 없으면 null
   */
  public record ProjectSummary(
      Long projectId,
      String name,
      String subtitle,
      ProjectCategory category,
      List<ProjectRole> roles,
      long recordCount,
      LocalDate lastRecordDate) {}
}
