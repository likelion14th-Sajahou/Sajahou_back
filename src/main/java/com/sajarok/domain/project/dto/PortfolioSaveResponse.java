package com.sajarok.domain.project.dto;

import com.sajarok.domain.project.dto.ProjectDetailResponse.Portfolio;
import com.sajarok.domain.project.entity.ProjectStatus;

/** 34번 포트폴리오 저장 응답. isFirstCompletion이 true면 이번에 완료로 바뀜, false면 다시 저장. */
public record PortfolioSaveResponse(
    ProjectStatus projectStatus, boolean isFirstCompletion, Portfolio portfolio) {}
