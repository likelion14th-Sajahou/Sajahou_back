package com.sajarok.global.time;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** DB 시각(한국 시간) ↔ API 시각(+09:00) 변환. */
class KstTimeTest {

  @Test
  @DisplayName("toOffset: DB 시각에 +09:00을 붙인다 (시각 값은 그대로)")
  void toOffset_addsKstOffset() {
    assertThat(KstTime.toOffset(LocalDateTime.of(2026, 11, 18, 21, 0)))
        .isEqualTo(OffsetDateTime.parse("2026-11-18T21:00:00+09:00"));
  }

  @Test
  @DisplayName("toLocal: +09:00은 그대로, UTC(+00:00)로 오면 같은 순간의 한국 시각(9시간 뒤)으로 바꾼다")
  void toLocal_convertsToKst() {
    assertThat(KstTime.toLocal(OffsetDateTime.parse("2026-11-08T23:59:00+09:00")))
        .isEqualTo(LocalDateTime.of(2026, 11, 8, 23, 59));
    assertThat(KstTime.toLocal(OffsetDateTime.parse("2026-11-08T14:59:00Z")))
        .isEqualTo(LocalDateTime.of(2026, 11, 8, 23, 59));
  }

  @Test
  @DisplayName("null이면 null")
  void nullStaysNull() {
    assertThat(KstTime.toOffset(null)).isNull();
    assertThat(KstTime.toLocal(null)).isNull();
  }
}
