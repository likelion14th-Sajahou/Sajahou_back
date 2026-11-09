package com.sajarok.domain.project.repository;

import java.time.LocalDateTime;

/** 프로젝트별 저장된(SAVED) 기록 수와 마지막 기록 시각. 기록이 없는 프로젝트는 결과에 없다. */
public record RecordStats(Long projectId, Long savedCount, LocalDateTime lastCreatedAt) {}
