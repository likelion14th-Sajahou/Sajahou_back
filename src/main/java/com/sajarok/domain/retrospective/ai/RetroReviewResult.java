package com.sajarok.domain.retrospective.ai;

import com.sajarok.global.exception.BusinessException;
import com.sajarok.global.exception.ErrorCode;
import com.sajarok.infra.ai.EvidenceVerifier;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 19번 AI 응답: 카드 제목·5칸·회고록 본문·근거. {@link #validate}를 통과해야 저장한다.
 *
 * <p>근거 칸이 없는 칸은 null(카드 blanks로 보인다). 근거 quote는 원문에 그대로 있어야 남기고, 없으면 그 근거만 뺀다(칸 내용은 살린다).
 */
public record RetroReviewResult(
    String title,
    String situation,
    String action,
    String reason,
    String result,
    String learned,
    String markdownBody,
    Map<String, List<Evidence>> evidence) {

  private static final Logger log = LoggerFactory.getLogger(RetroReviewResult.class);

  /** 카드 칸 key (evidence의 key). 22번 카드 상세의 칸과 같다. */
  public static final List<String> FIELDS =
      List.of("situation", "action", "reason", "result", "learned");

  /** story_card.title VARCHAR(200) */
  static final int MAX_TITLE = 200;

  /**
   * @param attachmentId quote가 나온 첨부. 회고 자료·질문 답에서 나왔으면 null
   * @param quote 원문 문장 그대로
   */
  public record Evidence(Long attachmentId, String quote) {}

  /**
   * 검증을 통과한 초안.
   *
   * @param markdownBody 회고록 본문(# 제목 없이). 서버가 제목을 붙인다
   * @param evidence 칸 key → 확인된 근거. 근거가 남지 않은 칸은 key가 없다
   */
  public record Draft(
      String title,
      String situation,
      String action,
      String reason,
      String result,
      String learned,
      String markdownBody,
      Map<String, List<Evidence>> evidence) {}

  /**
   * 제목과 회고록 본문은 꼭 있어야 한다(없으면 AI_FAILED). 공백뿐인 칸은 null로 본다.
   *
   * @param sourceText 회고 자료와 질문 답을 이은 글 (attachmentId가 null인 quote를 대조)
   * @param attachmentTexts 첨부 ID → 추출 텍스트 전체
   */
  public Draft validate(String sourceText, Map<Long, String> attachmentTexts) {
    if (isBlank(title) || isBlank(markdownBody)) {
      throw new BusinessException(ErrorCode.AI_FAILED);
    }
    String cleanTitle = title.strip();
    if (cleanTitle.length() > MAX_TITLE) {
      cleanTitle = cleanTitle.substring(0, MAX_TITLE);
    }

    Map<String, String> values = new LinkedHashMap<>();
    values.put("situation", clean(situation));
    values.put("action", clean(action));
    values.put("reason", clean(reason));
    values.put("result", clean(result));
    values.put("learned", clean(learned));

    Map<String, List<Evidence>> checked = new LinkedHashMap<>();
    Map<String, List<Evidence>> raw = evidence == null ? Map.of() : evidence;
    for (String field : FIELDS) {
      List<Evidence> items = raw.get(field);
      if (values.get(field) == null || items == null) {
        continue; // 빈 칸의 근거나, 근거가 없는 칸은 남기지 않는다
      }
      List<Evidence> verified =
          items.stream()
              .filter(Objects::nonNull)
              .map(e -> verify(e, sourceText, attachmentTexts))
              .filter(Objects::nonNull)
              .toList();
      if (!verified.isEmpty()) {
        checked.put(field, verified);
      }
    }

    return new Draft(
        cleanTitle,
        values.get("situation"),
        values.get("action"),
        values.get("reason"),
        values.get("result"),
        values.get("learned"),
        markdownBody.strip(),
        checked);
  }

  /** 원문에 있는 근거면 저장할 근거("실습: " 같은 칸 이름은 뗀 것)를, 넘겨준 첨부가 아니거나(지어낸 번호) 원문에 없으면 null을 돌려준다. */
  private static Evidence verify(Evidence e, String sourceText, Map<Long, String> attachmentTexts) {
    Long id = e.attachmentId();
    if (id != null && !attachmentTexts.containsKey(id)) {
      log.info("AI 회고록 근거가 넘겨주지 않은 첨부를 씀 attachmentId={} quote={}", id, e.quote());
      return null;
    }
    String original = id == null ? sourceText : attachmentTexts.get(id);
    String quote = EvidenceVerifier.verified(original, e.quote());
    return quote == null ? null : new Evidence(id, quote);
  }

  private static String clean(String value) {
    return isBlank(value) ? null : value.strip();
  }

  private static boolean isBlank(String value) {
    return value == null || value.isBlank();
  }
}
