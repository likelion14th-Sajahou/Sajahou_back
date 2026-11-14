package com.sajarok.domain.retrospective.ai;

import com.sajarok.domain.question.dto.NewQuestion;
import com.sajarok.domain.retrospective.entity.RetrospectiveType;
import com.sajarok.global.exception.BusinessException;
import com.sajarok.global.exception.ErrorCode;
import com.sajarok.infra.ai.EvidenceVerifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 17번 AI 응답: 자료 부족 여부와 질문. {@link #validate}를 통과해야 저장한다.
 *
 * <p>AI가 규칙을 조금 어겨도 쓸 수 있는 질문은 살린다(QA 11/12: 태그 표기가 조금 다르거나 질문이 많으면 전체가 502로 실패했음). 쓸 수 있는 질문이 하나도
 * 없을 때만 AI_FAILED이고, 버린 이유는 로그로 남긴다.
 */
public record RetroQuestionResult(Boolean insufficientMaterial, List<Question> questions) {

  private static final Logger log = LoggerFactory.getLogger(RetroQuestionResult.class);

  /** 회고 질문 태그 (ERD ai_question.tag). */
  public static final List<String> TAGS = List.of("근거와 숫자", "빠진 칸", "세션과 연결", "역량 신호", "느낀 점");

  /** 기능명세서 R-15(11/11): 최대 4개. 넘치면 앞에서 4개만 쓴다. */
  public static final int MAX_QUESTIONS = 4;

  private static final String FEELING = "느낀 점";

  /**
   * quote가 원문 대조에서 지워졌을 때 쓰는 태그별 고정 질문(QA 2). AI 질문은 "이 말을 듣고~"처럼 quote를 가리킬 수 있어서 quote 없이 남기지
   * 않는다. %s는 회고 종류(세션/과제).
   */
  static final Map<String, String> FALLBACK_QUESTIONS =
      Map.of(
          "근거와 숫자",
          "결과를 확인할 수 있는 숫자나 근거가 있나요? 확인한 것만 알려 주세요.",
          "빠진 칸",
          "짧게 쓴 부분이 있다면, 무엇을 했는지 조금 더 자세히 알려 주세요.",
          "세션과 연결",
          "이번 세션에서 배운 내용 중 실습이나 과제와 이어지는 부분이 있었나요?",
          "역량 신호",
          "이번 %s에서 직접 문제를 풀거나 팀과 함께 정한 일이 있었나요?",
          FEELING,
          "이번 %s에서 무엇을 느꼈나요?");

  /**
   * @param quote 질문의 근거가 된 원문 문장
   * @param sourceAttachmentId quote가 나온 첨부. 회고 자료(실습·과제·코드 리뷰·메모)에서 나왔으면 null
   */
  public record Question(String tag, String quote, Long sourceAttachmentId, String content) {}

  /**
   * @param questions 자료 부족이면 빈 목록
   */
  public record Validated(boolean insufficientMaterial, List<NewQuestion> questions) {}

  /**
   * 자료 부족이라고 하면 질문은 무시하고 부족으로 본다. 아니면 질문마다:
   *
   * <ul>
   *   <li>질문 글이 비었으면 버린다
   *   <li>태그는 띄어쓰기·가운뎃점만 다르면 목록의 태그로 맞추고, 그래도 목록에 없으면 버린다
   *   <li>출처(sourceAttachmentId)는 AI가 적은 번호가 아니라 quote가 실제로 있는 곳으로 정한다(회고 자료면 null, 첨부면 그 번호)
   *   <li>quote가 회고 자료·첨부 어디에도 없으면 quote·sourceAttachmentId를 null로 바꾸고, 질문 글은 태그별 고정 질문으로 바꾼다
   *   <li>느낀 점은 quote가 없으면(처음부터 null이든 지워졌든) 항상 고정 질문으로 바꾼다
   * </ul>
   *
   * 남은 질문이 없으면 AI_FAILED. 기능명세서 R-15대로 마지막은 항상 느낀 점 하나다(답이 카드 배운 점 R-20·회고록 느낀 점 R-19를 채움): 느낀 점이
   * 없으면 고정 질문을 끝에 붙이고, 중간에 있으면 끝으로 옮기고, 둘 이상이면 첫 번째만 쓴다. 느낀 점이 아닌 질문은 앞에서 3개까지만 쓴다.
   *
   * @param materialText 실습·과제·코드 리뷰·메모를 이은 글 (출처 첨부가 없는 quote를 대조)
   * @param attachmentTexts 첨부 ID → 추출 텍스트 전체
   * @param type 회고 종류. 고정 질문의 "이번 세션/과제"에 쓴다
   */
  public Validated validate(
      String materialText, Map<Long, String> attachmentTexts, RetrospectiveType type) {
    if (Boolean.TRUE.equals(insufficientMaterial)) {
      return new Validated(true, List.of());
    }
    List<Question> list = questions == null ? List.of() : questions;
    List<NewQuestion> checked =
        list.stream()
            .map(q -> check(q, materialText, attachmentTexts, type))
            .filter(Objects::nonNull)
            .toList();
    if (checked.isEmpty()) {
      log.warn("AI 회고 질문을 하나도 쓸 수 없음 받은 질문 수={} 내용={}", list.size(), list);
      throw new BusinessException(ErrorCode.AI_FAILED);
    }
    List<NewQuestion> usable = feelingLast(checked, type);
    if (usable.size() < Math.min(list.size(), MAX_QUESTIONS)) {
      log.warn("AI 회고 질문 일부를 버림 받은 {}개 → 쓴 {}개 내용={}", list.size(), usable.size(), list);
    }
    return new Validated(false, usable);
  }

  /** 쓸 수 없는 질문이면 null */
  private static NewQuestion check(
      Question q, String materialText, Map<Long, String> attachmentTexts, RetrospectiveType type) {
    if (q == null || isBlank(q.content())) {
      return null;
    }
    String tag = canonicalTag(q.tag());
    if (tag == null) {
      return null;
    }
    EvidenceVerifier.Located found =
        EvidenceVerifier.locate(q.quote(), q.sourceAttachmentId(), materialText, attachmentTexts);
    if (found != null) {
      return new NewQuestion(tag, found.quote(), found.attachmentId(), q.content().strip());
    }
    boolean quoteDropped = !isBlank(q.quote());
    if (quoteDropped || FEELING.equals(tag)) {
      String fixed = fallbackQuestion(tag, type);
      log.info("AI 회고 질문을 고정 질문으로 바꿈 tag={} 원래 질문={} 지워진 quote={}", tag, q.content(), q.quote());
      return new NewQuestion(tag, null, null, fixed);
    }
    return new NewQuestion(tag, null, null, q.content().strip());
  }

  /** 느낀 점이 아닌 질문(앞에서 3개까지) 뒤에 느낀 점 하나를 붙인다. 느낀 점이 없으면 고정 질문을 쓴다. */
  private static List<NewQuestion> feelingLast(List<NewQuestion> checked, RetrospectiveType type) {
    List<NewQuestion> result = new ArrayList<>();
    checked.stream()
        .filter(q -> !FEELING.equals(q.tag()))
        .limit(MAX_QUESTIONS - 1)
        .forEach(result::add);
    NewQuestion feeling =
        checked.stream().filter(q -> FEELING.equals(q.tag())).findFirst().orElse(null);
    if (feeling == null) {
      log.info("AI 회고 질문에 느낀 점이 없어 고정 질문을 끝에 붙임");
      feeling = new NewQuestion(FEELING, null, null, fallbackQuestion(FEELING, type));
    }
    result.add(feeling);
    return result;
  }

  static String fallbackQuestion(String tag, RetrospectiveType type) {
    String kind = type == RetrospectiveType.ASSIGNMENT ? "과제" : "세션";
    return FALLBACK_QUESTIONS.get(tag).formatted(kind);
  }

  /** "근거와숫자"·"근거와 숫자 "처럼 띄어쓰기나 가운뎃점만 다르면 목록의 태그로 맞춘다. 목록에 없으면 null */
  static String canonicalTag(String tag) {
    if (tag == null) {
      return null;
    }
    String key = normalize(tag);
    return TAGS.stream().filter(t -> normalize(t).equals(key)).findFirst().orElse(null);
  }

  private static String normalize(String s) {
    return s.replaceAll("[\\s·ㆍ・•]", "");
  }

  private static boolean isBlank(String value) {
    return value == null || value.isBlank();
  }
}
