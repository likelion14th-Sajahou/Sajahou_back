package com.sajarok.domain.project.ai;

import com.sajarok.global.exception.BusinessException;
import com.sajarok.global.exception.ErrorCode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 31번 AI 응답: 기록 5칸 제안. {@link #validate}를 통과해야 쓴다. */
public record RecordOrganizeResult(
    String did, String concern, String decision, String result, String nextStep) {

  /** 칸 key (31번 blanks 값). 이 순서로 응답한다. */
  public static final List<String> KEYS =
      List.of("did", "concern", "decision", "result", "nextStep");

  /** 28번 칸 상한과 같다. 넘으면 그대로 저장할 수 없어서 실패로 본다. */
  static final int MAX_LENGTH = 10000;

  /**
   * 공백뿐인 칸은 null(근거 없음)로 본다. 한 칸이라도 10,000자를 넘으면 AI_FAILED.
   *
   * @return key → 제안(없으면 null), {@link #KEYS} 순서
   */
  public Map<String, String> validate() {
    Map<String, String> byKey = new LinkedHashMap<>();
    byKey.put("did", clean(did));
    byKey.put("concern", clean(concern));
    byKey.put("decision", clean(decision));
    byKey.put("result", clean(result));
    byKey.put("nextStep", clean(nextStep));
    return byKey;
  }

  private static String clean(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    String stripped = value.strip();
    if (stripped.length() > MAX_LENGTH) {
      throw new BusinessException(ErrorCode.AI_FAILED);
    }
    return stripped;
  }
}
