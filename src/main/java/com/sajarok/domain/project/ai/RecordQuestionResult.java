package com.sajarok.domain.project.ai;

import com.sajarok.domain.question.dto.NewQuestion;
import com.sajarok.global.exception.BusinessException;
import com.sajarok.global.exception.ErrorCode;
import com.sajarok.infra.ai.EvidenceVerifier;
import java.util.List;
import java.util.Map;

/** 30번 AI 응답: 자료 부족 여부와 질문. {@link #validate}를 통과해야 저장한다. */
public record RecordQuestionResult(Boolean insufficientMaterial, List<Question> questions) {

  /** H-13 질문 태그. 질문이 최대 4개라 같은 태그가 겹칠 수 있다. */
  public static final List<String> TAGS = List.of("내 역할·과정", "선택의 이유", "근거와 숫자");

  public static final int MAX_QUESTIONS = 4;

  /**
   * @param quote 질문의 근거가 된 원문 문장
   * @param sourceAttachmentId quote가 나온 첨부. 메모·5칸에서 나왔으면 null
   */
  public record Question(String tag, String quote, Long sourceAttachmentId, String content) {}

  /**
   * @param questions 자료 부족이면 빈 목록
   */
  public record Validated(boolean insufficientMaterial, List<NewQuestion> questions) {}

  /**
   * 자료 부족이면 질문이 없어야 하고, 아니면 1 ~ 4개여야 한다. 태그는 목록 안, 질문은 비지 않아야 하고, 출처 첨부는 넘겨준 첨부여야 한다. 어기면
   * AI_FAILED.
   *
   * <p>quote가 원문에 그대로 없으면 quote·sourceAttachmentId를 null로 바꾼다(명세: 원문에 없으면 null). 질문 자체는 살린다.
   *
   * @param recordText 메모·5칸을 이은 글 (출처 첨부가 없는 quote를 대조)
   * @param attachmentTexts 첨부 ID → 추출 텍스트
   */
  public Validated validate(String recordText, Map<Long, String> attachmentTexts) {
    List<Question> list = questions == null ? List.of() : questions;
    if (Boolean.TRUE.equals(insufficientMaterial)) {
      if (!list.isEmpty()) {
        throw new BusinessException(ErrorCode.AI_FAILED);
      }
      return new Validated(true, List.of());
    }
    if (list.isEmpty() || list.size() > MAX_QUESTIONS) {
      throw new BusinessException(ErrorCode.AI_FAILED);
    }
    return new Validated(
        false, list.stream().map(q -> check(q, recordText, attachmentTexts)).toList());
  }

  private static NewQuestion check(
      Question q, String recordText, Map<Long, String> attachmentTexts) {
    if (q == null || !TAGS.contains(q.tag()) || isBlank(q.content())) {
      throw new BusinessException(ErrorCode.AI_FAILED);
    }
    Long source = q.sourceAttachmentId();
    if (source != null && !attachmentTexts.containsKey(source)) {
      throw new BusinessException(ErrorCode.AI_FAILED);
    }
    String original = source == null ? recordText : attachmentTexts.get(source);
    boolean quoted = EvidenceVerifier.isQuoted(original, q.quote());
    return quoted
        ? new NewQuestion(q.tag(), q.quote().strip(), source, q.content().strip())
        : new NewQuestion(q.tag(), null, null, q.content().strip());
  }

  private static boolean isBlank(String value) {
    return value == null || value.isBlank();
  }
}
