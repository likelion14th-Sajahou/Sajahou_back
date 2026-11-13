package com.sajarok.domain.storycard.ai;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.regex.Pattern;

/** 프로젝트 카드 제목 AI 응답. {@link #clean}을 거쳐 쓴다. 모르는 칸이 더 와도 버린다. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ProjectCardTitleResult(String title) {

  /** story_card.title VARCHAR(200). MySQL은 글자 수로 세므로 코드 포인트로 자른다. */
  static final int MAX_TITLE = 200;

  /** 앞의 # 기호·따옴표, 뒤의 따옴표·마침표. */
  private static final Pattern WRAPPERS =
      Pattern.compile("^[#\\s\"'“”‘’「」『』]+|[\\s\"'“”‘’「」『』.]+$");

  /**
   * 첫 줄만 쓰고, 앞뒤 공백·감싼 따옴표·앞의 # 기호·끝 마침표를 뗀다. 200자를 넘으면 자른다.
   *
   * @return 붙일 제목. 비었으면 null(제목을 빈칸으로 둠)
   */
  public String clean() {
    if (title == null || title.isBlank()) {
      return null;
    }
    String line = title.strip().lines().findFirst().orElse("");
    line = WRAPPERS.matcher(line).replaceAll("");
    if (line.isBlank()) {
      return null;
    }
    if (line.codePointCount(0, line.length()) <= MAX_TITLE) {
      return line;
    }
    return line.substring(0, line.offsetByCodePoints(0, MAX_TITLE)).strip();
  }
}
