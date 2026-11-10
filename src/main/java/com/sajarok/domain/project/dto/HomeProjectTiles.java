package com.sajarok.domain.project.dto;

import java.time.LocalDate;
import java.util.List;

/** 홈(4번)에 넣을 프로젝트 타일. 4번 명세의 inProgressProjects·doneProjects 형태 그대로다. */
public record HomeProjectTiles(
    List<InProgressTile> inProgressProjects, List<DoneTile> doneProjects) {

  /** 진행 중 프로젝트. lastRecordDate는 기록이 없으면 null. */
  public record InProgressTile(Long id, String name, long recordCount, LocalDate lastRecordDate) {}

  /** 완료한 프로젝트. 화면에 '기록 N'만 보여서 기록 수만 넣는다. */
  public record DoneTile(Long id, String name, long recordCount) {}
}
