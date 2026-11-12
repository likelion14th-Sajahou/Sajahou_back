package com.sajarok.infra.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sajarok.global.exception.BusinessException;
import com.sajarok.global.exception.ErrorCode;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class AiLockTest {

  private static final String KEY = "project-record:77";

  private final AiLock aiLock = new AiLock();

  @Test
  void rejectsConcurrentRequestForSameKey() throws Exception {
    var firstStarted = new CountDownLatch(1);
    var allowFirstToFinish = new CountDownLatch(1);
    var executor = Executors.newSingleThreadExecutor();

    try {
      var first =
          executor.submit(
              () ->
                  aiLock.run(
                      KEY,
                      () -> {
                        firstStarted.countDown();
                        try {
                          if (!allowFirstToFinish.await(5, TimeUnit.SECONDS)) {
                            throw new AssertionError("첫 작업을 끝내라는 신호가 오지 않았어요.");
                          }
                        } catch (InterruptedException e) {
                          Thread.currentThread().interrupt();
                          throw new AssertionError(e);
                        }
                        return "첫 작업 완료";
                      }));

      assertThat(firstStarted.await(5, TimeUnit.SECONDS)).isTrue();

      assertThatThrownBy(
              () ->
                  aiLock.run(
                      KEY,
                      () -> {
                        throw new AssertionError("두 번째 작업이 실행됐어요.");
                      }))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.AI_IN_PROGRESS);

      allowFirstToFinish.countDown();
      assertThat(first.get(5, TimeUnit.SECONDS)).isEqualTo("첫 작업 완료");
    } finally {
      allowFirstToFinish.countDown();
      executor.shutdownNow();
    }
  }

  @Test
  void allowsRetryForSameKeyAfterFailure() {
    var failure = new IllegalStateException("AI 호출 실패");

    assertThatThrownBy(
            () ->
                aiLock.run(
                    KEY,
                    () -> {
                      throw failure;
                    }))
        .isSameAs(failure);

    assertThat(aiLock.run(KEY, () -> "재시도 성공")).isEqualTo("재시도 성공");
  }
}
