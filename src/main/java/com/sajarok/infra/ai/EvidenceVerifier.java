package com.sajarok.infra.ai;

import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * AI가 준 인용문(quote)이 원문에 그대로 있는지 확인한다. 질문 출처·카드 근거·공고 분석 근거가 지어낸 문장이 아닌지 막는 용도다.
 *
 * <p>띄어쓰기와 줄바꿈은 모두 빼고 비교한다. PDF에서 뽑은 글은 한글 단어가 줄 끝에서 잘려 '예\n시이며'처럼 나오는데, AI는 인용할 때 '예시이며'로 붙여 쓰기
 * 때문이다. AI가 인용문 앞뒤에 붙이곤 하는 따옴표·말줄임표·마침표도 떼고 비교한다.
 *
 * <p>프롬프트는 사용자 글을 "실습: …", "한 일: …", "A1. …"처럼 칸 이름을 붙여 넘기는데, AI가 인용할 때 이 칸 이름까지 옮기는 일이 많다. 그대로는
 * 원문에 없지만 칸 이름을 떼면 있는 경우, 뗀 문장을 인용문으로 쓴다.
 */
public final class EvidenceVerifier {

  private static final Logger log = LoggerFactory.getLogger(EvidenceVerifier.class);

  private static final Pattern WHITESPACE = Pattern.compile("\\s+");

  /** 인용문 앞뒤에서 떼는 글자: 따옴표, 낫표, 말줄임표, 마침표 */
  private static final Pattern WRAPPERS = Pattern.compile("^[\"'“”‘’「」『』….]+|[\"'“”‘’「」『』….]+$");

  /** 인용문 맨 앞의 칸 이름: "실습: ", "고민·해결: ", "A1. " 같은 것 (12자 이하) */
  private static final Pattern LABEL = Pattern.compile("^(?:[^:：\\n]{1,12}[:：]|[QA]\\d+[.)])\\s*");

  /** 로그에 남기는 인용문 최대 글자 수 */
  private static final int LOG_MAX = 200;

  private EvidenceVerifier() {}

  public static boolean isQuoted(String original, String quote) {
    return verified(original, quote) != null;
  }

  /**
   * 원문에 있는 인용문을 돌려준다. 저장·화면 표시에 쓸 글이다.
   *
   * @return 그대로 있으면 앞뒤 공백만 뗀 인용문, 칸 이름을 떼야 있으면 칸 이름을 뗀 인용문, 원문에 없으면 null
   */
  public static String verified(String original, String quote) {
    if (quote == null || compactQuote(quote).isEmpty()) {
      return null;
    }
    String compactOriginal = compact(original);
    String stripped = quote.strip();
    if (compactOriginal.contains(compactQuote(stripped))) {
      return stripped;
    }
    String unlabeled = LABEL.matcher(stripped).replaceFirst("").strip();
    if (!unlabeled.equals(stripped)
        && !compactQuote(unlabeled).isEmpty()
        && compactOriginal.contains(compactQuote(unlabeled))) {
      return unlabeled;
    }
    log.info("AI 인용문이 원문에 없어 출처를 지움 quote={}", abbreviate(quote));
    return null;
  }

  private static String compactQuote(String quote) {
    return WRAPPERS.matcher(compact(quote)).replaceAll("");
  }

  private static String compact(String text) {
    return text == null ? "" : WHITESPACE.matcher(text).replaceAll("");
  }

  private static String abbreviate(String text) {
    return text.length() <= LOG_MAX ? text : text.substring(0, LOG_MAX) + "…";
  }
}
