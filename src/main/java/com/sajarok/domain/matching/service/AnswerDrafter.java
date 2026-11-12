package com.sajarok.domain.matching.service;

import com.sajarok.domain.matching.ai.AnswerDraftResult;
import com.sajarok.domain.matching.ai.MatchingPrompts;
import com.sajarok.domain.matching.ai.MatchingPrompts.PostingText;
import com.sajarok.domain.matching.entity.AnswerDraft;
import com.sajarok.domain.matching.entity.AnswerDraft.Segment;
import com.sajarok.domain.matching.entity.AnswerDraft.Source;
import com.sajarok.domain.matching.service.CardRecommender.Recommendation;
import com.sajarok.domain.storycard.dto.CardSnapshot;
import com.sajarok.infra.ai.AiClient;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** 37번 AI 답 초안: 프롬프트 조립 → AI 호출 → 검증(AnswerDraftResult.toDraft). 트랜잭션 안에서 부르지 않는다(최대 50초). */
@Component
@RequiredArgsConstructor
public class AnswerDrafter {

  private final AiClient aiClient;

  /** 빈칸만 있는 초안의 빈칸들. M-10 빈칸 안내 형식("항목 · 채울 내용"). */
  private static final List<Segment> BLANKS =
      List.of(
          new Segment("blank", null, null, "situation", "상황 · 어떤 상황이었는지 채워 주세요"),
          new Segment("blank", null, null, "action", "행동 · 내가 한 일을 채워 주세요"),
          new Segment("blank", null, null, "reason", "이유 · 그렇게 한 이유를 채워 주세요"),
          new Segment("blank", null, null, "result", "결과 · 숫자나 받은 반응을 채워 주세요"));

  /**
   * @param cards 카드 ID → 카드 (추천 카드 내용을 프롬프트에 넣을 때 씀)
   */
  public AnswerDraft draft(
      String question,
      Integer charLimit,
      Recommendation recommendation,
      Map<Long, CardSnapshot> cards,
      PostingText posting) {
    List<Source> sources = recommendation.sources();
    String user = MatchingPrompts.answerDraftUser(question, charLimit, sources, cards, posting);

    return aiClient
        .call(MatchingPrompts.ANSWER_DRAFT_SYSTEM, user, AnswerDraftResult.class)
        .toDraft(sources, recommendation.missingCompetencyIds(), false);
  }

  /**
   * 빈칸만 있는 초안. 맞는 카드가 없을 때 쓴다(AI를 부르지 않음, 출처 없음).
   *
   * @param sources 출처 목록. 맞는 카드가 없을 때는 빈 목록(공고를 쓴 문장도 없으므로 usesJobPosting false)
   */
  public static AnswerDraft blankDraft(
      List<Source> sources, List<Long> missingCompetencyIds, boolean noMatchingCard) {
    return new AnswerDraft(BLANKS, sources, missingCompetencyIds, noMatchingCard);
  }
}
