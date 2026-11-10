package com.sajarok.domain.matching.dto;

import com.sajarok.domain.matching.entity.AnswerDraft.Segment;
import com.sajarok.domain.matching.entity.CoverLetterCardRole;
import com.sajarok.domain.storycard.entity.StoryCardType;
import java.util.List;

/** 37번 답 초안 응답. 39번의 questions[].answer도 같은 모양이다. */
public record AnswerResponse(
    Long questionId,
    List<Segment> segments,
    int charCount,
    int blankCount,
    List<CardRef> cards,
    boolean usesJobPosting,
    List<MissingCompetency> missingCompetencies,
    boolean noMatchingCard) {

  /** 답에 쓴 카드. no는 segment.source가 가리키는 출처 번호. */
  public record CardRef(
      int no,
      CoverLetterCardRole role,
      Long cardId,
      StoryCardType type,
      String title,
      Integer weekNumber,
      String projectName) {}

  /** 맞는 카드가 없던 문항 역량 (M-13). */
  public record MissingCompetency(Long competencyId, String name) {}
}
