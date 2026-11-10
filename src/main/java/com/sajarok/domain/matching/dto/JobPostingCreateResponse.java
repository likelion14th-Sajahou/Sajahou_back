package com.sajarok.domain.matching.dto;

import com.sajarok.domain.matching.dto.JobPostingDetailResponse.PostingCompetencyItem;
import java.util.List;

/** 36번 공고 저장 응답. competencies는 39번과 같은 모양이다. */
public record JobPostingCreateResponse(
    Long jobPostingId,
    List<PostingCompetencyItem> competencies,
    boolean noCompetencyFound,
    List<QuestionItem> questions) {

  /** 저장한 문항과 AI가 고른 문항별 역량. */
  public record QuestionItem(
      Long questionId, int seq, String content, Integer charLimit, List<Long> competencyIds) {}
}
