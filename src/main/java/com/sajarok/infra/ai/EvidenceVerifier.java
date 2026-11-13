package com.sajarok.infra.ai;

import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * AI가 준 인용문(quote)이 원문에 그대로 있는지 확인한다. 질문 출처·카드 근거·공고 분석 근거가 지어낸 문장이 아닌지 막는 용도다.
 *
 * <p>띄어쓰기와 줄바꿈은 모두 빼고 비교한다. PDF에서 뽑은 글은 한글 단어가 줄 끝에서 잘려 '예\n시이며'처럼 나오는데, AI는 인용할 때 '예시이며'로 붙여 쓰기
 * 때문이다. AI가 인용문 앞뒤에 붙이곤 하는 따옴표·말줄임표·마침표도 떼고 비교한다.
 */
public final class EvidenceVerifier {

  private static final Logger log = LoggerFactory.getLogger(EvidenceVerifier.class);

  private static final Pattern WHITESPACE = Pattern.compile("\\s+");

  /** 인용문 앞뒤에서 떼는 글자: 따옴표, 낫표, 말줄임표, 마침표 */
  private static final Pattern WRAPPERS = Pattern.compile("^[\"'“”‘’「」『』….]+|[\"'“”‘’「」『』….]+$");

  /** 로그에 남기는 인용문 최대 글자 수 */
  private static final int LOG_MAX = 200;

  private EvidenceVerifier() {}

  public static boolean isQuoted(String original, String quote) {
    String compactQuote = WRAPPERS.matcher(compact(quote)).replaceAll("");
    if (compactQuote.isEmpty()) {
      return false;
    }
    boolean found = compact(original).contains(compactQuote);
    if (!found) {
      log.info("AI 인용문이 원문에 없어 출처를 지움 quote={}", abbreviate(quote));
    }
    return found;
  }

  private static String compact(String text) {
    return text == null ? "" : WHITESPACE.matcher(text).replaceAll("");
  }

  private static String abbreviate(String text) {
    return text.length() <= LOG_MAX ? text : text.substring(0, LOG_MAX) + "…";
  }
}
