package com.sajarok.domain.project.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sajarok.domain.project.repository.ProjectRecordRepository;
import com.sajarok.domain.project.repository.RecordDate;
import com.sajarok.global.exception.BusinessException;
import com.sajarok.global.exception.ErrorCode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProjectRecordServiceTest {

  @Mock private ProjectRecordRepository projectRecordRepository;

  @InjectMocks private ProjectRecordService projectRecordService;

  @Test
  @DisplayName("getOwnerId: 기록이 있으면 프로젝트 주인 회원 ID를, 없으면 NOT_FOUND를 준다")
  void getOwnerId_returnsOwnerOrThrowsNotFound() {
    when(projectRecordRepository.findUserIdById(10L)).thenReturn(Optional.of(1L));
    when(projectRecordRepository.findUserIdById(999L)).thenReturn(Optional.empty());

    assertThat(projectRecordService.getOwnerId(10L)).isEqualTo(1L);
    assertThatThrownBy(() -> projectRecordService.getOwnerId(999L))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.NOT_FOUND);
  }

  @Test
  @DisplayName("findDates: 기록 ID → 기록 날짜(시각을 뺀 날짜) Map을 준다")
  void findDates_returnsIdToDateMap() {
    when(projectRecordRepository.findDatesByIdIn(List.of(10L, 11L)))
        .thenReturn(
            List.of(
                new RecordDate(10L, LocalDateTime.of(2026, 11, 4, 23, 59)),
                new RecordDate(11L, LocalDateTime.of(2026, 11, 5, 0, 1))));

    assertThat(projectRecordService.findDates(List.of(10L, 11L)))
        .isEqualTo(Map.of(10L, LocalDate.of(2026, 11, 4), 11L, LocalDate.of(2026, 11, 5)));
  }

  @Test
  @DisplayName("findDates: 빈 목록이면 쿼리 없이 빈 Map을 준다")
  void findDates_emptyInput_returnsEmptyWithoutQuery() {
    assertThat(projectRecordService.findDates(List.of())).isEmpty();
    verify(projectRecordRepository, never()).findDatesByIdIn(anyCollection());
  }
}
