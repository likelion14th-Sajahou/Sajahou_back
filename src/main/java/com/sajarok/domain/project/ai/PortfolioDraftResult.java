package com.sajarok.domain.project.ai;

import com.sajarok.global.exception.BusinessException;
import com.sajarok.global.exception.ErrorCode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 33번 AI 응답: 포트폴리오 항목별 본문. Markdown 전체가 아니라 항목별 내용만 받는다.
 *
 * <p>제목·항목 순서·## 제목·빈칸 문구는 서버(PortfolioMarkdown)가 붙인다. AI가 순서나 제목을 바꾸거나 빼지 못하게 하고, blanks를 서버가 정확히
 * 계산하기 위해서다.
 */
public record PortfolioDraftResult(List<Section> sections) {

  /** 항목 key (33번 blanks 값). 이 순서대로 포트폴리오를 조립한다. */
  public static final List<String> KEYS =
      List.of("role", "contribution", "problem", "result", "artifact", "learned");

  /**
   * @param body 항목 본문 Markdown(제목 없이). 근거가 없으면 null
   */
  public record Section(String key, String body) {}

  /**
   * 6개 key가 빠짐없이 한 번씩 있는지 확인한다. 아니면 AI_FAILED. 공백뿐인 body는 null(빈칸)로 본다.
   *
   * @return key → 본문(없으면 null), {@link #KEYS} 순서
   */
  public Map<String, String> validate() {
    if (sections == null || sections.size() != KEYS.size()) {
      throw new BusinessException(ErrorCode.AI_FAILED);
    }
    Map<String, String> byKey = new LinkedHashMap<>();
    for (Section section : sections) {
      if (section == null || !KEYS.contains(section.key()) || byKey.containsKey(section.key())) {
        throw new BusinessException(ErrorCode.AI_FAILED);
      }
      String body = section.body();
      byKey.put(section.key(), body == null || body.isBlank() ? null : body.strip());
    }
    Map<String, String> ordered = new LinkedHashMap<>();
    KEYS.forEach(key -> ordered.put(key, byKey.get(key)));
    return ordered;
  }
}
