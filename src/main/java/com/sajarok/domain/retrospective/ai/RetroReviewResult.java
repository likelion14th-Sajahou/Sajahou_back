package com.sajarok.domain.retrospective.ai;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.sajarok.global.exception.BusinessException;
import com.sajarok.global.exception.ErrorCode;
import com.sajarok.infra.ai.EvidenceVerifier;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 19번 AI 응답: 카드 5칸·회고록 본문·근거. {@link #validate}를 통과해야 저장한다. 카드 제목은 AI가 쓰지 않고 서버가 세션명으로 붙인다.
 *
 * <p>근거 칸이 없는 칸은 null(카드 blanks로 보인다). 근거 quote는 원문에 그대로 있어야 남기고, 없으면 그 근거만 뺀다(칸 내용은 살린다). 근거의
 * attachmentId는 AI가 적은 번호가 아니라 quote가 실제로 있는 곳으로 정한다.
 *
 * <p>AI가 예전 습관대로 title 같은 칸을 더 보내도 실패로 보지 않고 버린다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record RetroReviewResult(
    String situation,
    String action,
    String reason,
    String result,
    String learned,
    String markdownBody,
    Map<String, List<Evidence>> evidence) {

  /** 카드 칸 key (evidence의 key). 22번 카드 상세의 칸과 같다. */
  public static final List<String> FIELDS =
      List.of("situation", "action", "reason", "result", "learned");

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
      String situation,
      String action,
      String reason,
      String result,
      String learned,
      String markdownBody,
      Map<String, List<Evidence>> evidence) {}

  /**
   * 회고록 본문은 꼭 있어야 한다(없으면 AI_FAILED). 공백뿐인 칸은 null로 본다.
   *
   * @param sourceText 회고 자료와 질문 답을 이은 글 (attachmentId가 null인 quote를 대조)
   * @param attachmentTexts 첨부 ID → 추출 텍스트 전체
   */
  public Draft validate(String sourceText, Map<Long, String> attachmentTexts) {
    if (isBlank(markdownBody)) {
      throw new BusinessException(ErrorCode.AI_FAILED);
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
        values.get("situation"),
        values.get("action"),
        values.get("reason"),
        values.get("result"),
        values.get("learned"),
        markdownBody.strip(),
        checked);
  }

  /** 회고 자료·답·첨부 어딘가에 있는 근거면 실제 출처로 고친 근거를, 어디에도 없으면 null을 돌려준다. */
  private static Evidence verify(Evidence e, String sourceText, Map<Long, String> attachmentTexts) {
    EvidenceVerifier.Located found =
        EvidenceVerifier.locate(e.quote(), e.attachmentId(), sourceText, attachmentTexts);
    return found == null ? null : new Evidence(found.attachmentId(), found.quote());
  }

  private static String clean(String value) {
    return isBlank(value) ? null : value.strip();
  }

  private static boolean isBlank(String value) {
    return value == null || value.isBlank();
  }
}
