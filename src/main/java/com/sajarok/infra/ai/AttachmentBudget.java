package com.sajarok.infra.ai;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/**
 * AI 프롬프트에 넣는 첨부 글자 예산. 첨부를 다 합쳐 {@link #TOTAL_CHARS}자까지 넣는다.
 *
 * <p>합이 예산 안이면 모두 그대로 넣는다. 넘치면 짧은 첨부는 다 넣고, 남은 예산을 긴 첨부끼리 똑같이 나눠 각자 앞부분만 넣는다. 그래서 첨부가 하나면 4만 자까지 다
 * 읽고, 여러 개여도 프롬프트가 4만 자를 넘지 않아 AI 응답 시간(50초 제한)이 크게 늘지 않는다.
 *
 * <p>quote 검증은 여기서 자른 글이 아니라 자르지 않은 전체 첨부 글로 한다.
 */
public final class AttachmentBudget {

  /** 첨부를 다 합친 최대 글자 수. 노션 세션 자료(약 3만 자)를 한 번에 다 읽을 수 있게 정했다. */
  public static final int TOTAL_CHARS = 40_000;

  private AttachmentBudget() {}

  /**
   * @param texts 첨부 추출 글 (null은 빈 글로 본다)
   * @return 같은 순서로, 예산에 맞춰 앞부분만 남긴 글
   */
  public static List<String> fit(List<String> texts) {
    return fit(texts, TOTAL_CHARS);
  }

  static List<String> fit(List<String> texts, int total) {
    int n = texts.size();
    int[] lengths = new int[n];
    for (int i = 0; i < n; i++) {
      lengths[i] = texts.get(i) == null ? 0 : texts.get(i).length();
    }

    // 짧은 첨부부터 남은 예산을 (남은 첨부 수)로 나눈 몫까지 준다. 다 못 쓴 몫은 다음(더 긴) 첨부들이 나눠 쓴다.
    Integer[] order = new Integer[n];
    Arrays.setAll(order, i -> i);
    Arrays.sort(order, Comparator.comparingInt(i -> lengths[i]));
    int[] limits = new int[n];
    int remaining = total;
    for (int k = 0; k < n; k++) {
      int i = order[k];
      limits[i] = Math.min(lengths[i], remaining / (n - k));
      remaining -= limits[i];
    }

    List<String> result = new ArrayList<>(n);
    for (int i = 0; i < n; i++) {
      result.add(head(texts.get(i), limits[i]));
    }
    return result;
  }

  private static String head(String text, int limit) {
    if (text == null) {
      return "";
    }
    if (text.length() <= limit) {
      return text;
    }
    int end = limit;
    if (end > 0 && Character.isHighSurrogate(text.charAt(end - 1))) {
      end--; // 이모지처럼 두 글자로 된 문자를 반으로 자르지 않는다
    }
    return text.substring(0, end);
  }
}
