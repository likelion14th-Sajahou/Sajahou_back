package com.sajarok.global.time;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/**
 * DB 시각(DATETIME, 시간대 없음 = 한국 시간)과 API 시각(+09:00이 붙은 ISO-8601)을 서로 바꾼다.
 *
 * <p>DB 시각이 한국 시간이라는 전제다. JVM 시간대(Asia/Seoul) 설정이 필요하다.
 */
public final class KstTime {

  public static final ZoneOffset OFFSET = ZoneOffset.ofHours(9);

  private KstTime() {}

  /** 응답용: DB 시각 → 2026-11-18T21:00:00+09:00. null이면 null. */
  public static OffsetDateTime toOffset(LocalDateTime dateTime) {
    return dateTime == null ? null : dateTime.atOffset(OFFSET);
  }

  /** 요청용: 어떤 시간대로 와도 같은 순간의 한국 시각으로 바꿔 DB 시각으로. null이면 null. */
  public static LocalDateTime toLocal(OffsetDateTime dateTime) {
    return dateTime == null ? null : dateTime.withOffsetSameInstant(OFFSET).toLocalDateTime();
  }
}
