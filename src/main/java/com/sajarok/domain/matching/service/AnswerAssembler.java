package com.sajarok.domain.matching.service;

import com.sajarok.domain.matching.dto.AnswerResponse;
import com.sajarok.domain.matching.dto.AnswerResponse.CardRef;
import com.sajarok.domain.matching.dto.AnswerResponse.MissingCompetency;
import com.sajarok.domain.matching.entity.AnswerDraft;
import com.sajarok.domain.matching.entity.AnswerDraft.Segment;
import com.sajarok.domain.matching.entity.AnswerDraft.Source;
import com.sajarok.domain.matching.entity.CoverLetterCardRole;
import com.sajarok.domain.matching.entity.CoverLetterQuestion;
import com.sajarok.domain.storycard.dto.CardSnapshot;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * 저장된 초안(AnswerDraft)을 37번 응답 모양으로 만든다. 37·39번이 같이 써서 두 응답 모양이 어긋나지 않게 한다.
 *
 * <p>DB를 읽지 않는다. 필요한 카드·역할·프로젝트 이름·역량 이름은 부르는 쪽이 한 번에 읽어서 넘긴다 (문항마다 읽지 않음, N+1 방지).
 */
@Component
public class AnswerAssembler {

  private static final String CARD = "CARD";
  private static final String JOB_POSTING = "JOB_POSTING";

  /**
   * @param roles 이 문항의 카드 ID → 역할 (cover_letter_question_card)
   * @param liveCards 지금 공개된 내 카드 (카드 ID → 카드). 여기 없는 카드는 지워졌거나 비공개로 본다.
   * @param projectNames 프로젝트 ID → 이름
   * @param competencyNames 역량 ID → 이름 (missingCompetencies 이름). 없는 ID는 이름 null
   * @return 초안이 없으면 null
   */
  public AnswerResponse assemble(
      CoverLetterQuestion question,
      Map<Long, CoverLetterCardRole> roles,
      Map<Long, CardSnapshot> liveCards,
      Map<Long, String> projectNames,
      Map<Long, String> competencyNames) {
    AnswerDraft draft = question.getAnswerDraft();
    if (draft == null) {
      return null;
    }
    List<Source> sources = draft.sources() == null ? List.of() : draft.sources();

    // 지워졌거나 비공개가 된 카드 출처 번호. 그 문장은 글을 남기고 source만 비운다.
    Set<Integer> deadSourceNos =
        sources.stream()
            .filter(s -> CARD.equals(s.kind()) && !liveCards.containsKey(s.cardId()))
            .map(Source::no)
            .collect(Collectors.toSet());

    List<Segment> segments =
        draft.segments() == null
            ? List.of()
            : draft.segments().stream()
                .map(seg -> deadSourceNos.contains(seg.source()) ? seg.withoutSource() : seg)
                .toList();

    List<CardRef> cards =
        sources.stream()
            .filter(s -> CARD.equals(s.kind()) && liveCards.containsKey(s.cardId()))
            .map(s -> toCardRef(s, roles.get(s.cardId()), liveCards.get(s.cardId()), projectNames))
            .toList();

    List<MissingCompetency> missing =
        draft.missingCompetencyIds() == null
            ? List.of()
            : draft.missingCompetencyIds().stream()
                .map(id -> new MissingCompetency(id, competencyNames.get(id)))
                .toList();

    return new AnswerResponse(
        question.getId(),
        segments,
        draft.charCount(),
        draft.blankCount(),
        cards,
        sources.stream().anyMatch(s -> JOB_POSTING.equals(s.kind())),
        missing,
        draft.noMatchingCard());
  }

  private CardRef toCardRef(
      Source source, CoverLetterCardRole role, CardSnapshot card, Map<Long, String> projectNames) {
    return new CardRef(
        source.no(),
        role,
        card.cardId(),
        card.type(),
        card.title(),
        // TODO(주차 담당): 회고 카드면 weekId로 주차 번호를 채운다. 지금은 null.
        null,
        card.projectId() == null ? null : projectNames.get(card.projectId()));
  }

  /** 카드 목록 → 카드 ID로 찾는 맵. */
  public static Map<Long, CardSnapshot> byId(List<CardSnapshot> cards) {
    return cards.stream()
        .filter(Objects::nonNull)
        .collect(Collectors.toMap(CardSnapshot::cardId, c -> c, (a, b) -> a));
  }
}
