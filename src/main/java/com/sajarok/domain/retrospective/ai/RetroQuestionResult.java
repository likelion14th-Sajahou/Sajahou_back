package com.sajarok.domain.retrospective.ai;

import com.sajarok.domain.question.dto.NewQuestion;
import com.sajarok.global.exception.BusinessException;
import com.sajarok.global.exception.ErrorCode;
import com.sajarok.infra.ai.EvidenceVerifier;
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

  /** WBS BE2-08: 회고 질문은 최대 5개. 넘치면 앞에서 5개만 쓴다. */
  public static final int MAX_QUESTIONS = 5;

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
   *   <li>넘겨주지 않은 첨부 번호거나 quote가 원문에 그대로 없으면 quote·sourceAttachmentId를 null로 바꾼다(질문은 살린다)
   * </ul>
   *
   * 남은 질문이 없으면 AI_FAILED, 5개가 넘으면 앞에서 5개만 쓴다.
   *
   * @param materialText 실습·과제·코드 리뷰·메모를 이은 글 (출처 첨부가 없는 quote를 대조)
   * @param attachmentTexts 첨부 ID → 추출 텍스트 전체
   */
  public Validated validate(String materialText, Map<Long, String> attachmentTexts) {
    if (Boolean.TRUE.equals(insufficientMaterial)) {
      return new Validated(true, List.of());
    }
    List<Question> list = questions == null ? List.of() : questions;
    List<NewQuestion> usable =
        list.stream()
            .map(q -> check(q, materialText, attachmentTexts))
            .filter(Objects::nonNull)
            .limit(MAX_QUESTIONS)
            .toList();
    if (usable.isEmpty()) {
      log.warn("AI 회고 질문을 하나도 쓸 수 없음 받은 질문 수={} 내용={}", list.size(), list);
      throw new BusinessException(ErrorCode.AI_FAILED);
    }
    if (usable.size() < Math.min(list.size(), MAX_QUESTIONS)) {
      log.warn("AI 회고 질문 일부를 버림 받은 {}개 → 쓴 {}개 내용={}", list.size(), usable.size(), list);
    }
    return new Validated(false, usable);
  }

  /** 쓸 수 없는 질문이면 null */
  private static NewQuestion check(
      Question q, String materialText, Map<Long, String> attachmentTexts) {
    if (q == null || isBlank(q.content())) {
      return null;
    }
    String tag = canonicalTag(q.tag());
    if (tag == null) {
      return null;
    }
    Long source = q.sourceAttachmentId();
    if (source != null && !attachmentTexts.containsKey(source)) {
      return new NewQuestion(tag, null, null, q.content().strip());
    }
    String original = source == null ? materialText : attachmentTexts.get(source);
    boolean quoted = EvidenceVerifier.isQuoted(original, q.quote());
    return quoted
        ? new NewQuestion(tag, q.quote().strip(), source, q.content().strip())
        : new NewQuestion(tag, null, null, q.content().strip());
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
