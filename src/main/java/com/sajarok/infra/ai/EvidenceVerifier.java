package com.sajarok.infra.ai;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
 *
 * <p>출처는 AI가 적은 번호를 믿지 않고 {@link #locate}로 문장이 실제로 있는 곳을 찾아 정한다. AI가 PDF 문장을 인용하고도 출처를 비우거나(사용자
 * 글이라고 적거나) 다른 첨부 번호를 적는 일이 있기 때문이다.
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

  /**
   * 원문에서 찾은 인용문과 그 문장이 실제로 있는 출처.
   *
   * @param quote 저장·화면 표시에 쓸 인용문 (칸 이름을 떼야 찾았으면 뗀 것)
   * @param attachmentId 문장이 있는 첨부. 사용자가 쓴 글(회고 자료·기록 칸·질문 답)에 있으면 null
   */
  public record Located(String quote, Long attachmentId) {}

  public static boolean isQuoted(String original, String quote) {
    return verified(original, quote) != null;
  }

  /**
   * 원문 하나에서 인용문을 찾는다.
   *
   * @return 그대로 있으면 앞뒤 공백만 뗀 인용문, 칸 이름을 떼야 있으면 칸 이름을 뗀 인용문, 원문에 없으면 null
   */
  public static String verified(String original, String quote) {
    if (isEmptyQuote(quote)) {
      return null;
    }
    String found = match(original, quote);
    if (found == null) {
      log.info("AI 인용문이 원문에 없어 출처를 지움 quote={}", abbreviate(quote));
    }
    return found;
  }

  /**
   * 인용문이 실제로 있는 곳을 찾는다. AI가 적은 곳을 먼저 보고, 없으면 사용자 글, 그다음 첨부를 차례로 본다.
   *
   * @param quote AI가 준 인용문
   * @param claimedAttachmentId AI가 적은 첨부 번호. null이면 AI는 사용자 글에서 나왔다고 한 것
   * @param ownText 사용자가 쓴 글
   * @param attachmentTexts 넘겨준 첨부 ID → 추출 글 (자르지 않은 전체)
   * @return 찾으면 인용문과 실제 출처, 어디에도 없으면(지어낸 문장) null
   */
  public static Located locate(
      String quote, Long claimedAttachmentId, String ownText, Map<Long, String> attachmentTexts) {
    if (isEmptyQuote(quote)) {
      return null;
    }
    List<Long> order = new ArrayList<>();
    if (claimedAttachmentId != null && attachmentTexts.containsKey(claimedAttachmentId)) {
      order.add(claimedAttachmentId);
    }
    order.add(null); // 사용자 글
    attachmentTexts.keySet().stream().filter(id -> !order.contains(id)).forEach(order::add);

    for (Long id : order) {
      String found = match(id == null ? ownText : attachmentTexts.get(id), quote);
      if (found != null) {
        if (!Objects.equals(id, claimedAttachmentId)) {
          log.info(
              "AI가 적은 출처와 실제 출처가 달라 바로잡음 claimed={} actual={} quote={}",
              claimedAttachmentId,
              id,
              abbreviate(quote));
        }
        return new Located(found, id);
      }
    }
    log.info("AI 인용문이 원문에 없어 출처를 지움 quote={}", abbreviate(quote));
    return null;
  }

  /** 원문에 있으면 저장할 인용문, 없으면 null (로그는 남기지 않는다) */
  private static String match(String original, String quote) {
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
    return null;
  }

  private static boolean isEmptyQuote(String quote) {
    return quote == null || compactQuote(quote).isEmpty();
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
