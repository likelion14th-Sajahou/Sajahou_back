package com.sajarok.domain.project.ai;

import com.sajarok.domain.question.dto.NewQuestion;
import com.sajarok.global.exception.BusinessException;
import com.sajarok.global.exception.ErrorCode;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 32번 AI 응답: 포트폴리오에 빠진 항목을 묻는 마치기 질문.
 *
 * <p>엔티티에 바로 넣지 않고 {@link #validate}를 통과해야 저장한다. AI가 이상한 값을 줘도 DB에 그대로 들어가지 않게 한다.
 */
public record FinishQuestionResult(List<Question> questions) {

  /** H-17 빈 곳 태그. 이 순서대로 seq를 매긴다(화면에 항상 같은 순서로 보이게). */
  public static final List<String> TAGS = List.of("내 역할", "최종 결과", "근거와 숫자", "산출물 요청", "배운 점");

  public static final int MAX_QUESTIONS = TAGS.size();

  /**
   * @param quote 빈 곳 이유 (H-17 오른쪽 빈 곳 목록에 보여 줌)
   */
  public record Question(String tag, String quote, String content) {}

  /**
   * 태그가 목록 안인지, 태그마다 질문이 하나인지, 질문·이유가 비지 않았는지 확인한다. 아니면 AI_FAILED.
   *
   * <p>빠진 항목이 없으면 질문 0개도 정상이다. 결과는 태그 순서({@link #TAGS})로 정렬한다.
   */
  public List<NewQuestion> validate() {
    if (questions == null || questions.size() > MAX_QUESTIONS) {
      throw new BusinessException(ErrorCode.AI_FAILED);
    }
    Set<String> seen = new HashSet<>();
    for (Question q : questions) {
      if (q == null
          || !TAGS.contains(q.tag())
          || !seen.add(q.tag())
          || isBlank(q.content())
          || isBlank(q.quote())) {
        throw new BusinessException(ErrorCode.AI_FAILED);
      }
    }
    return questions.stream()
        .sorted(Comparator.comparingInt(q -> TAGS.indexOf(q.tag())))
        .map(q -> new NewQuestion(q.tag(), q.quote().strip(), null, q.content().strip()))
        .toList();
  }

  private static boolean isBlank(String value) {
    return value == null || value.isBlank();
  }
}
