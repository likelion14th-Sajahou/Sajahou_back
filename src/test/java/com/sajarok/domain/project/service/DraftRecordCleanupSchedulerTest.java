package com.sajarok.domain.project.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DraftRecordCleanupSchedulerTest {

  private static final LocalDateTime CUTOFF = LocalDateTime.of(2026, 11, 9, 12, 0);

  @Mock private ProjectRecordService projectRecordService;

  @InjectMocks private DraftRecordCleanupScheduler scheduler;

  @Test
  @DisplayName("한 기록 삭제가 실패해도 나머지는 계속 지우고, 실제로 지운 개수만 센다")
  void cleanup_oneFails_continuesWithOthers() {
    when(projectRecordService.findExpiredDraftIds(CUTOFF, DraftRecordCleanupScheduler.BATCH_SIZE))
        .thenReturn(List.of(1L, 2L, 3L, 4L));
    when(projectRecordService.deleteExpiredDraft(1L, CUTOFF)).thenReturn(true);
    when(projectRecordService.deleteExpiredDraft(2L, CUTOFF))
        .thenThrow(new IllegalStateException("FK"));
    when(projectRecordService.deleteExpiredDraft(3L, CUTOFF)).thenReturn(false); // 그 사이 저장됨
    when(projectRecordService.deleteExpiredDraft(4L, CUTOFF)).thenReturn(true);

    assertThat(scheduler.cleanup(CUTOFF)).isEqualTo(2);
    verify(projectRecordService).deleteExpiredDraft(4L, CUTOFF);
  }

  @Test
  @DisplayName("지울 기록이 없으면 아무것도 지우지 않는다")
  void cleanup_nothingToDelete() {
    when(projectRecordService.findExpiredDraftIds(CUTOFF, DraftRecordCleanupScheduler.BATCH_SIZE))
        .thenReturn(List.of());

    assertThat(scheduler.cleanup(CUTOFF)).isZero();
  }
}
