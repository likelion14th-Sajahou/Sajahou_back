package com.sajarok.domain.matching.service;

import com.sajarok.domain.competency.service.CompetencyService;
import com.sajarok.domain.matching.dto.AnswerResponse;
import com.sajarok.domain.matching.entity.AnswerDraft;
import com.sajarok.domain.matching.entity.CoverLetterCardRole;
import com.sajarok.domain.matching.entity.CoverLetterQuestion;
import com.sajarok.domain.matching.entity.CoverLetterQuestionCard;
import com.sajarok.domain.matching.repository.CoverLetterQuestionCardRepository;
import com.sajarok.domain.matching.repository.CoverLetterQuestionRepository;
import com.sajarok.domain.project.service.ProjectService;
import com.sajarok.domain.storycard.dto.CardSnapshot;
import com.sajarok.domain.storycard.service.StoryCardService;
import com.sajarok.domain.week.service.WeekService;
import com.sajarok.global.exception.BusinessException;
import com.sajarok.global.exception.ErrorCode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 37번 저장 트랜잭션. AI를 부른 뒤(트랜잭션 밖) 결과만 짧게 저장한다.
 *
 * <p>CoverLetterQuestionService 안에서 바로 하지 않는 이유: 같은 객체 안 호출은 @Transactional이 적용되지 않아서 따로 둔다(36번
 * JobPostingWriter와 같은 구조).
 */
@Component
@RequiredArgsConstructor
public class AnswerDraftWriter {

  private final CoverLetterQuestionRepository coverLetterQuestionRepository;
  private final CoverLetterQuestionCardRepository coverLetterQuestionCardRepository;
  private final StoryCardService storyCardService;
  private final ProjectService projectService;
  private final CompetencyService competencyService;
  private final WeekService weekService;
  private final AnswerAssembler answerAssembler;

  /**
   * 초안을 저장하고(고친 답은 비움) 문항-카드 연결을 새로 넣은 뒤 37번 응답을 만든다.
   *
   * <p>AI를 기다리는 동안 문항이 지워졌으면 NOT_FOUND. 카드가 지워졌거나 비공개가 됐으면 그 카드는 연결하지 않는다(FK 오류 방지). 응답에서는
   * AnswerAssembler가 그 카드 출처를 빼고 문장의 source만 비운다.
   *
   * @param recommendedCards 추천 카드 (0번째 MAIN, 1번째 SUB)
   */
  @Transactional
  public AnswerResponse save(
      Long userId, Long questionId, AnswerDraft draft, List<CardSnapshot> recommendedCards) {
    CoverLetterQuestion question =
        coverLetterQuestionRepository
            .findByIdAndUserId(questionId, userId)
            .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
    question.applyDraft(draft);

    Map<Long, CardSnapshot> liveCards =
        AnswerAssembler.byId(storyCardService.findPublished(userId));
    coverLetterQuestionCardRepository.deleteAllByQuestionId(questionId);
    Map<Long, CoverLetterCardRole> roles = new HashMap<>();
    List<CoverLetterQuestionCard> links = new ArrayList<>();
    for (int i = 0; i < recommendedCards.size(); i++) {
      Long cardId = recommendedCards.get(i).cardId();
      if (!liveCards.containsKey(cardId)) {
        continue;
      }
      CoverLetterCardRole role = i == 0 ? CoverLetterCardRole.MAIN : CoverLetterCardRole.SUB;
      roles.put(cardId, role);
      links.add(new CoverLetterQuestionCard(questionId, cardId, role));
    }
    coverLetterQuestionCardRepository.saveAll(links);

    Map<Long, String> projectNames =
        projectService.names(
            roles.keySet().stream()
                .map(id -> liveCards.get(id).projectId())
                .filter(Objects::nonNull)
                .collect(Collectors.toSet()));
    Map<Long, Integer> weekNumbers =
        weekService.findWeekNumbers(
            AnswerAssembler.weekIds(roles.keySet().stream().map(liveCards::get).toList()));
    Map<Long, String> competencyNames =
        competencyService.names(
            draft.missingCompetencyIds() == null ? List.of() : draft.missingCompetencyIds());
    return answerAssembler.assemble(
        question, roles, liveCards, projectNames, weekNumbers, competencyNames);
  }
}
