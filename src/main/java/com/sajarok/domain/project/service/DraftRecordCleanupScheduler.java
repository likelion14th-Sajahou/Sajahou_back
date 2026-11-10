package com.sajarok.domain.project.service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 'AI와 정리하기'를 시작하고 24시간 넘게 저장하지 않은 DRAFT 기록을 지운다 (27번 명세: 첨부·AI 질문과 함께).
 *
 * <p>매시 정각(한국 시간)에 오래된 순으로 최대 {@link #BATCH_SIZE}개를 지운다. 남은 것은 다음 실행에서 지운다. 기록마다 따로 트랜잭션이라 하나가 실패해도
 * 나머지는 지우고, 실패한 기록은 다음 실행에서 다시 시도한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DraftRecordCleanupScheduler {

  static final Duration DRAFT_TTL = Duration.ofHours(24);
  static final int BATCH_SIZE = 100;

  private final ProjectRecordService projectRecordService;

  /**
   * zone은 실행 시각(매시 정각)만 정한다. 기준 시각은 created_at을 채우는 JPA Auditing과 같은 시계(JVM 기본 시간대의
   * LocalDateTime.now())로 계산해야 둘이 어긋나지 않는다.
   */
  @Scheduled(cron = "0 0 * * * *", zone = "Asia/Seoul")
  public void run() {
    cleanup(LocalDateTime.now().minus(DRAFT_TTL));
  }

  /** cutoff보다 먼저 만든 DRAFT 기록을 지우고, 지운 개수를 돌려준다. */
  int cleanup(LocalDateTime cutoff) {
    List<Long> ids = projectRecordService.findExpiredDraftIds(cutoff, BATCH_SIZE);
    int deleted = 0;
    for (Long id : ids) {
      try {
        if (projectRecordService.deleteExpiredDraft(id, cutoff)) {
          deleted++;
        }
      } catch (RuntimeException e) {
        log.warn("DRAFT 기록 정리 실패 recordId={}", id, e);
      }
    }
    if (!ids.isEmpty()) {
      log.info("DRAFT 기록 정리: 대상 {}개 중 {}개 삭제 (cutoff={})", ids.size(), deleted, cutoff);
    }
    return deleted;
  }
}
