package com.sajarok.domain.project.repository;

import java.time.LocalDateTime;

/** 기록 ID와 만든 시각만 읽을 때 쓰는 조회 결과. */
public record RecordDate(Long recordId, LocalDateTime createdAt) {}
