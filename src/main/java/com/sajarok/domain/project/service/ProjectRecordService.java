package com.sajarok.domain.project.service;

import com.sajarok.domain.project.repository.ProjectRecordRepository;
import com.sajarok.domain.project.repository.RecordDate;
import com.sajarok.global.exception.BusinessException;
import com.sajarok.global.exception.ErrorCode;
import java.time.LocalDate;
import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProjectRecordService {

  private final ProjectRecordRepository projectRecordRepository;

  /** 기록 주인 회원 ID. 다른 도메인이 소유 확인에 쓴다. 없으면 NOT_FOUND. */
  public Long getOwnerId(Long recordId) {
    return projectRecordRepository
        .findUserIdById(recordId)
        .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
  }

  /** 기록 ID → 기록 날짜(만든 날). 없는 ID는 결과에서 빠진다. */
  public Map<Long, LocalDate> findDates(Collection<Long> recordIds) {
    if (recordIds == null || recordIds.isEmpty()) {
      return Map.of();
    }
    return projectRecordRepository.findDatesByIdIn(recordIds).stream()
        .collect(Collectors.toMap(RecordDate::recordId, r -> r.createdAt().toLocalDate()));
  }
}
