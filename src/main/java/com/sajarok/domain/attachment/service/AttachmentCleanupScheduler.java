package com.sajarok.domain.attachment.service;

import java.time.Duration;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class AttachmentCleanupScheduler {

  private static final Duration RETENTION = Duration.ofHours(24);
  private static final int BATCH_SIZE = 100;

  private final AttachmentService attachmentService;

  @Scheduled(cron = "0 10 * * * *", zone = "Asia/Seoul")
  public void run() {
    cleanup(LocalDateTime.now().minus(RETENTION));
  }

  void cleanup(LocalDateTime cutoff) {
    for (Long id : attachmentService.findExpiredUnlinkedIds(cutoff, BATCH_SIZE)) {
      try {
        attachmentService.deleteExpiredUnlinked(id, cutoff);
      } catch (RuntimeException e) {
        log.warn("미연결 첨부 정리 실패 attachmentId={}", id, e);
      }
    }
  }
}
