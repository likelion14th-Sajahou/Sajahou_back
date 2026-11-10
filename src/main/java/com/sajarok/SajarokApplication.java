package com.sajarok;

import java.util.TimeZone;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class SajarokApplication {

  static final String TIME_ZONE = "Asia/Seoul";

  /*
   * 클래스가 처음 읽힐 때(앱 실행·@SpringBootTest 모두) 기본 시간대를 한국으로 맞춘다. DB 컬럼이 DATETIME(시간대 없음)이고
   * created_at·updated_at(JPA Auditing)이 JVM 시간대로 찍히는데, 응답은 이 값을 한국 시간으로 보고 +09:00을 붙인다(KstTime).
   * 서버가 UTC면 9시간 어긋나므로 서버 설정과 관계없이 여기서 고정한다.
   */
  static {
    useKoreanTimeZone();
  }

  public static void main(String[] args) {
    SpringApplication.run(SajarokApplication.class, args);
  }

  static void useKoreanTimeZone() {
    TimeZone.setDefault(TimeZone.getTimeZone(TIME_ZONE));
  }
}
