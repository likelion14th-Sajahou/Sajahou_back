package com.sajarok.domain.matching.dto;

import com.sajarok.domain.attachment.dto.AttachmentView;
import java.util.List;

/** 39번 매칭 결과(공고 상세) 응답. */
public record JobPostingDetailResponse(
    Long jobPostingId,
    String company,
    String department,
    String postingText,
    String jd,
    String preferredQualification,
    String companyInfo,
    List<AttachmentView> attachments,
    List<PostingCompetencyItem> competencies,
    List<QuestionItem> questions) {

  /** 공고가 원하는 역량. cardCount는 이 역량 태그가 붙은 내 공개 카드 수. */
  public record PostingCompetencyItem(
      Long competencyId, String name, String evidenceQuote, long cardCount) {}

  /** 문항. answer는 초안(37번)을 만들기 전이면 null. */
  public record QuestionItem(
      Long questionId,
      int seq,
      String content,
      Integer charLimit,
      List<Long> competencyIds,
      AnswerResponse answer,
      String editedAnswer) {}
}
