package com.sajarok.domain.question.dto;

import java.util.Collection;
import java.util.List;

public record NewQuestion(String tag, String quote, Long sourceAttachmentId, String content) {

  /**
   * 짚은 문장(quote)이 없는 질문은 자료 전체에서 묻는 질문이다. AI가 읽은 첨부가 하나뿐이면 그 첨부를 출처로 붙여, 화면이 "📄 파일 이름 · 전체에서
   * 물어봐요"로 보여 줄 수 있게 한다. 첨부가 없거나 둘 이상이면 어느 파일인지 몰라 출처를 비운다(null).
   *
   * @param attachmentIds AI에게 넘긴(추출이 끝난) 첨부 ID
   */
  public static List<NewQuestion> withWholeMaterialSource(
      List<NewQuestion> questions, Collection<Long> attachmentIds) {
    if (attachmentIds == null || attachmentIds.size() != 1) {
      return questions;
    }
    Long only = attachmentIds.iterator().next();
    return questions.stream()
        .map(q -> q.quote() == null ? new NewQuestion(q.tag(), null, only, q.content()) : q)
        .toList();
  }
}
