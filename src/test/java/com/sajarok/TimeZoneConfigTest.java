package com.sajarok;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.TimeZone;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** JVM 기본 시간대 고정: 서버가 UTC여도 LocalDateTime.now()(created_at 등)가 한국 시간이 되는지. */
class TimeZoneConfigTest {

  private TimeZone original;

  @BeforeEach
  void rememberAndSetUtc() {
    original = TimeZone.getDefault();
    // 맥 로컬은 이미 한국 시간이라, 배포 서버처럼 UTC에서 시작해야 확인이 된다.
    TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
  }

  @AfterEach
  void restore() {
    TimeZone.setDefault(original);
  }

  @Test
  @DisplayName("UTC 서버에서도 설정 후 기본 시간대는 Asia/Seoul이고, LocalDateTime.now()가 한국 시각이다")
  void useKoreanTimeZone_fromUtc() {
    SajarokApplication.useKoreanTimeZone();

    assertThat(TimeZone.getDefault().getID()).isEqualTo("Asia/Seoul");
    assertThat(LocalDateTime.now())
        .isCloseTo(LocalDateTime.now(ZoneId.of("Asia/Seoul")), within(5, ChronoUnit.SECONDS));
  }
}
