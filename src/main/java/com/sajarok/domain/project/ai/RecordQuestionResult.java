package com.sajarok.domain.project.ai;

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
 * 30번 AI 응답: 자료 부족 여부와 질문. {@link #validate}를 통과해야 저장한다.
 *
 * <p>AI가 규칙을 조금 어겨도 쓸 수 있는 질문은 살린다(QA 11/12: 태그 표기 차이·질문 초과로 요청 전체가 502로 실패했음). 쓸 수 있는 질문이 하나도 없을
 * 때만 AI_FAILED이고, 버린 이유는 로그로 남긴다. 회고 17번 RetroQuestionResult와 같은 규칙.
 */
public record RecordQuestionResult(Boolean insufficientMaterial, List<Question> questions) {

  private static final Logger log = LoggerFactory.getLogger(RecordQuestionResult.class);

  /** H-13 질문 태그. 질문이 최대 4개라 같은 태그가 겹칠 수 있다. */
  public static final List<String> TAGS = List.of("내 역할·과정", "선택의 이유", "근거와 숫자");

  /** 최대 4개. 넘치면 앞에서 4개만 쓴다. */
  public static final int MAX_QUESTIONS = 4;

  /**
   * quote가 원문 대조에서 지워졌을 때 쓰는 태그별 고정 질문(QA 2). AI 질문은 "이 말을 듣고~"처럼 quote를 가리킬 수 있어서 quote 없이 남기지
   * 않는다. quote를 처음부터 null로 준 질문은 규칙상 정상이라 바꾸지 않는다.
   */
  static final Map<String, String> FALLBACK_QUESTIONS =
      Map.of(
          "내 역할·과정", "이 기록에서 내가 맡은 일과 진행 과정을 알려 주세요.",
          "선택의 이유", "무엇을 골랐고, 왜 그렇게 정했어요?",
          "근거와 숫자", "결과나 팀·사용자 반응이 있었나요? 확인한 것만요.");

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
   * 자료 부족이라고 하면 질문은 무시하고 부족으로 본다. 아니면 질문마다:
   *
   * <ul>
   *   <li>질문 글이 비었으면 버린다
   *   <li>태그는 띄어쓰기·가운뎃점만 다르면 목록의 태그로 맞추고, 그래도 목록에 없으면 버린다
   *   <li>출처(sourceAttachmentId)는 AI가 적은 번호가 아니라 quote가 실제로 있는 곳으로 정한다(메모·5칸이면 null, 첨부면 그 번호)
   *   <li>quote가 메모·5칸·첨부 어디에도 없으면 quote·sourceAttachmentId를 null로 바꾸고(명세: 원문에 없으면 null), 질문 글은 태그별
   *       고정 질문으로 바꾼다
   *   <li>quote가 없는 질문(자료 전체에서 묻는 질문)은 AI가 읽은 첨부가 하나뿐이면 그 첨부를 출처로 둔다. 응답의 quote는 "전체에서 물어봐요"
   * </ul>
   *
   * 남은 질문이 없으면 AI_FAILED, 4개가 넘으면 앞에서 4개만 쓴다.
   *
   * @param recordText 메모·5칸을 이은 글 (출처 첨부가 없는 quote를 대조)
   * @param attachmentTexts 첨부 ID → 추출 텍스트
   */
  public Validated validate(String recordText, Map<Long, String> attachmentTexts) {
    if (Boolean.TRUE.equals(insufficientMaterial)) {
      return new Validated(true, List.of());
    }
    List<Question> list = questions == null ? List.of() : questions;
    List<NewQuestion> usable =
        list.stream()
            .map(q -> check(q, recordText, attachmentTexts))
            .filter(Objects::nonNull)
            .limit(MAX_QUESTIONS)
            .toList();
    if (usable.isEmpty()) {
      log.warn("AI 기록 질문을 하나도 쓸 수 없음 받은 질문 수={} 내용={}", list.size(), list);
      throw new BusinessException(ErrorCode.AI_FAILED);
    }
    if (usable.size() < Math.min(list.size(), MAX_QUESTIONS)) {
      log.warn("AI 기록 질문 일부를 버림 받은 {}개 → 쓴 {}개 내용={}", list.size(), usable.size(), list);
    }
    return new Validated(
        false, NewQuestion.withWholeMaterialSource(usable, attachmentTexts.keySet()));
  }

  /** 쓸 수 없는 질문이면 null */
  private static NewQuestion check(
      Question q, String recordText, Map<Long, String> attachmentTexts) {
    if (q == null || isBlank(q.content())) {
      return null;
    }
    String tag = canonicalTag(q.tag());
    if (tag == null) {
      return null;
    }
    EvidenceVerifier.Located found =
        EvidenceVerifier.locate(q.quote(), q.sourceAttachmentId(), recordText, attachmentTexts);
    if (found != null) {
      return new NewQuestion(tag, found.quote(), found.attachmentId(), q.content().strip());
    }
    if (!isBlank(q.quote())) {
      log.info("AI 기록 질문을 고정 질문으로 바꿈 tag={} 원래 질문={} 지워진 quote={}", tag, q.content(), q.quote());
      return new NewQuestion(tag, null, null, FALLBACK_QUESTIONS.get(tag));
    }
    return new NewQuestion(tag, null, null, q.content().strip());
  }

  /** "내 역할 · 과정"·"근거와숫자"처럼 띄어쓰기나 가운뎃점만 다르면 목록의 태그로 맞춘다. 목록에 없으면 null */
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
