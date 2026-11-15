package com.sajarok.domain.question.dto;

import com.sajarok.domain.question.entity.AiQuestion;
import com.sajarok.domain.question.entity.QuestionStatus;

/**
 * AI 질문 한 개 (13·17·30·32번 응답).
 *
 * @param quote 질문의 근거가 된 원문 문장. 짚은 문장이 없는 질문(자료 전체에서 묻는 질문)은 {@link #WHOLE_MATERIAL}
 * @param sourceAttachmentId quote가 나온 첨부. 사용자가 쓴 칸에서 나왔으면 null. 짚은 문장이 없는 질문은 AI가 읽은 첨부가 하나뿐이면 그
 *     첨부, 아니면 null
 */
public record QuestionView(
    Long questionId,
    int seq,
    String tag,
    String quote,
    Long sourceAttachmentId,
    String content,
    String answer,
    QuestionStatus status) {

  /**
   * quote가 없는 질문에 대신 내보내는 문구. 화면은 "📄 파일 이름 · 전체에서 물어봐요"처럼 보여 준다. DB에는 null로 두고 응답에서만 바꾼다(원문 대조·AI
   * 프롬프트에 섞이지 않게, 이 기능 전에 만든 질문에도 똑같이 보이게).
   */
  public static final String WHOLE_MATERIAL = "전체에서 물어봐요";

  public static QuestionView from(AiQuestion q) {
    String quote = q.getQuote();
    return new QuestionView(
        q.getId(),
        q.getSeq(),
        q.getTag(),
        quote == null || quote.isBlank() ? WHOLE_MATERIAL : quote,
        q.getSourceAttachmentId(),
        q.getContent(),
        q.getAnswer(),
        q.getStatus());
  }
}
