package com.sajarok.domain.project.service;

import com.sajarok.domain.project.ai.FinishAttachments;
import com.sajarok.domain.project.ai.FinishPrompts;
import com.sajarok.domain.project.ai.PortfolioDraftResult;
import com.sajarok.domain.project.entity.Project;
import com.sajarok.domain.project.entity.ProjectRecord;
import com.sajarok.domain.question.dto.QuestionView;
import com.sajarok.global.exception.BusinessException;
import com.sajarok.infra.ai.AiClient;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 33번 AI 포트폴리오 초안: 프롬프트 조립 → AI 호출 → 검증. 트랜잭션 안에서 부르지 않는다.
 *
 * <p>답한 마치기 질문의 항목이 비어 오면(QA 6) 한 번 다시 요청한다. 전체가 50초(api-spec 공통 안내)를 넘지 않게 남은 시간으로만 다시 부르고, 다시 받은
 * 결과에서는 비었던 항목만 가져온다(멀쩡하던 항목이 바뀌지 않게).
 */
@Component
@RequiredArgsConstructor
public class PortfolioDrafter {

  private static final Logger log = LoggerFactory.getLogger(PortfolioDrafter.class);

  static final Duration TOTAL_BUDGET = Duration.ofSeconds(50);

  /** 남은 시간이 이보다 적으면 다시 요청하지 않는다(첫 호출보다 짧으면 끝까지 못 받을 가능성이 커서). */
  static final Duration MIN_RETRY_TIME = Duration.ofSeconds(20);

  /** 마치기 질문 태그 → 포트폴리오 항목 (FinishPrompts 규칙 5) */
  static final Map<String, String> TAG_TO_KEY =
      Map.of(
          "내 역할", "role",
          "최종 결과", "result",
          "근거와 숫자", "result",
          "산출물 요청", "artifact",
          "배운 점", "learned");

  private final AiClient aiClient;

  /** 테스트에서 시간을 바꿔 넣으려고 final로 두지 않는다. */
  Clock clock = Clock.systemUTC();

  /**
   * @param answered 답한(ANSWERED) 마치기 질문
   * @return key → 본문(없으면 null)
   */
  public Map<String, String> draft(
      Project project,
      List<ProjectRecord> records,
      List<QuestionView> answered,
      FinishAttachments attachments) {
    String user = FinishPrompts.portfolioUser(project, records, answered, attachments);
    Instant start = clock.instant();
    Map<String, String> first =
        aiClient
            .call(FinishPrompts.PORTFOLIO_DRAFT_SYSTEM, user, PortfolioDraftResult.class)
            .validate();
    Duration elapsed = Duration.between(start, clock.instant());

    List<String> missing = missingAnswered(first, answered, attachments);
    if (missing.isEmpty()) {
      return first;
    }
    Duration remaining = TOTAL_BUDGET.minus(elapsed);
    if (remaining.compareTo(MIN_RETRY_TIME) < 0) {
      log.warn(
          "33번 답한 항목이 비었지만 시간이 모자라 다시 요청하지 않음 missing={} 첫 호출={}ms", missing, elapsed.toMillis());
      return first;
    }

    log.info("33번 답한 항목이 비어 다시 요청함 missing={} 첫 호출={}ms", missing, elapsed.toMillis());
    Map<String, String> retry;
    try {
      retry =
          aiClient
              .call(
                  FinishPrompts.PORTFOLIO_DRAFT_SYSTEM,
                  FinishPrompts.portfolioRetryUser(user, missing),
                  PortfolioDraftResult.class,
                  remaining)
              .validate();
    } catch (BusinessException e) {
      log.warn("33번 다시 요청이 실패해 첫 결과를 씀 missing={} code={}", missing, e.getErrorCode());
      return first;
    }

    Map<String, String> merged = new LinkedHashMap<>(first);
    missing.forEach(key -> merged.put(key, retry.get(key)));
    List<String> stillMissing = missing.stream().filter(key -> merged.get(key) == null).toList();
    if (!stillMissing.isEmpty()) {
      log.warn("33번 다시 요청해도 비어 있음 keys={}", stillMissing);
    }
    return merged;
  }

  /**
   * 답이 있는데 비어 온 항목. 산출물은 첨부가 있을 때만 본다(H-17 "아직 없어요"는 첨부가 없어 산출물이 비는 게 맞다).
   *
   * @return PortfolioDraftResult.KEYS 순서
   */
  static List<String> missingAnswered(
      Map<String, String> bodies, List<QuestionView> answered, FinishAttachments attachments) {
    Set<String> answeredKeys =
        answered.stream()
            .map(q -> hasContent(q, attachments) ? TAG_TO_KEY.get(q.tag()) : null)
            .filter(key -> key != null)
            .collect(Collectors.toSet());
    return PortfolioDraftResult.KEYS.stream()
        .filter(answeredKeys::contains)
        .filter(key -> bodies.get(key) == null)
        .toList();
  }

  /** 산출물 요청은 첨부만 붙이고 답 글이 없을 수 있어서 첨부로 보고, 나머지는 답 글이 있는지 본다. */
  private static boolean hasContent(QuestionView q, FinishAttachments attachments) {
    if ("artifact".equals(TAG_TO_KEY.get(q.tag()))) {
      return !attachments.artifacts().isEmpty();
    }
    return q.answer() != null && !q.answer().isBlank();
  }
}
