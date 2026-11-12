package com.sajarok.domain.matching.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.groups.Tuple.tuple;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.sajarok.domain.competency.service.CompetencyService;
import com.sajarok.domain.matching.dto.AnswerResponse;
import com.sajarok.domain.matching.entity.AnswerDraft;
import com.sajarok.domain.matching.entity.AnswerDraft.Segment;
import com.sajarok.domain.matching.entity.CoverLetterCardRole;
import com.sajarok.domain.matching.entity.CoverLetterQuestion;
import com.sajarok.domain.matching.entity.CoverLetterQuestionCard;
import com.sajarok.domain.matching.repository.CoverLetterQuestionCardRepository;
import com.sajarok.domain.matching.repository.CoverLetterQuestionRepository;
import com.sajarok.domain.project.service.ProjectService;
import com.sajarok.domain.storycard.dto.CardSnapshot;
import com.sajarok.domain.storycard.entity.StoryCardType;
import com.sajarok.domain.storycard.service.StoryCardService;
import com.sajarok.global.exception.BusinessException;
import com.sajarok.global.exception.ErrorCode;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

/** 37번 저장 테스트: 초안 저장·고친 답 비우기, 카드 연결 다시 넣기, 그 사이 지워진 카드·문항 처리. */
@ExtendWith(MockitoExtension.class)
class AnswerDraftWriterTest {

  @Mock private CoverLetterQuestionRepository coverLetterQuestionRepository;
  @Mock private CoverLetterQuestionCardRepository coverLetterQuestionCardRepository;
  @Mock private StoryCardService storyCardService;
  @Mock private ProjectService projectService;
  @Mock private CompetencyService competencyService;
  @Mock private AnswerAssembler answerAssembler;

  @InjectMocks private AnswerDraftWriter answerDraftWriter;

  private static final AnswerDraft DRAFT =
      new AnswerDraft(
          List.of(new Segment("text", "중커톤에서 팀 API 연동을 맡았습니다.", 1, null, null)),
          List.of(),
          List.of(),
          false);

  private static CardSnapshot card(Long id) {
    return new CardSnapshot(
        id, StoryCardType.PROJECT, null, 3L, "카드 " + id, null, null, null, null, null, Set.of(3L));
  }

  private static CoverLetterQuestion editedQuestion() {
    CoverLetterQuestion q = CoverLetterQuestion.builder().seq(1).content("협업 경험").build();
    ReflectionTestUtils.setField(q, "id", 55L);
    q.editAnswer("예전에 고친 답");
    return q;
  }

  @Test
  @DisplayName("37번 저장: 새 초안을 넣고 고친 답을 비운 뒤, 기존 카드 연결을 지우고 MAIN·SUB로 다시 넣는다")
  @SuppressWarnings("unchecked")
  void save_replacesDraftAndLinks() {
    CoverLetterQuestion question = editedQuestion();
    when(coverLetterQuestionRepository.findByIdAndUserId(55L, 1L))
        .thenReturn(Optional.of(question));
    when(storyCardService.findPublished(1L)).thenReturn(List.of(card(410L), card(300L)));
    when(projectService.names(Set.of(3L))).thenReturn(Map.of(3L, "사자록"));
    AnswerResponse response =
        new AnswerResponse(55L, List.of(), 0, 0, List.of(), false, List.of(), false);
    when(answerAssembler.assemble(
            eq(question), anyMap(), anyMap(), eq(Map.of(3L, "사자록")), anyMap()))
        .thenReturn(response);

    AnswerResponse result = answerDraftWriter.save(1L, 55L, DRAFT, List.of(card(410L), card(300L)));

    assertThat(result).isSameAs(response);
    assertThat(question.getAnswerDraft()).isEqualTo(DRAFT);
    assertThat(question.getEditedAnswer()).isNull();
    InOrder order = inOrder(coverLetterQuestionCardRepository);
    order.verify(coverLetterQuestionCardRepository).deleteAllByQuestionId(55L);
    ArgumentCaptor<List<CoverLetterQuestionCard>> links = ArgumentCaptor.forClass(List.class);
    order.verify(coverLetterQuestionCardRepository).saveAll(links.capture());
    assertThat(links.getValue())
        .extracting(link -> link.getId().getCardId(), CoverLetterQuestionCard::getRole)
        .containsExactly(
            tuple(410L, CoverLetterCardRole.MAIN), tuple(300L, CoverLetterCardRole.SUB));
  }

  @Test
  @DisplayName("37번 저장: AI를 기다리는 사이 지워졌거나 비공개가 된 카드는 연결하지 않는다 (FK 오류 방지)")
  @SuppressWarnings("unchecked")
  void save_skipsCardsDeletedDuringAi() {
    when(coverLetterQuestionRepository.findByIdAndUserId(55L, 1L))
        .thenReturn(Optional.of(editedQuestion()));
    when(storyCardService.findPublished(1L)).thenReturn(List.of(card(300L)));

    answerDraftWriter.save(1L, 55L, DRAFT, List.of(card(410L), card(300L)));

    ArgumentCaptor<List<CoverLetterQuestionCard>> links = ArgumentCaptor.forClass(List.class);
    verify(coverLetterQuestionCardRepository).saveAll(links.capture());
    assertThat(links.getValue())
        .extracting(link -> link.getId().getCardId(), CoverLetterQuestionCard::getRole)
        .containsExactly(tuple(300L, CoverLetterCardRole.SUB));
  }

  @Test
  @DisplayName("37번 저장: AI를 기다리는 사이 문항이 지워졌으면 NOT_FOUND, 카드 연결은 건드리지 않는다")
  void save_questionDeleted_throwsNotFound() {
    when(coverLetterQuestionRepository.findByIdAndUserId(55L, 1L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> answerDraftWriter.save(1L, 55L, DRAFT, List.of(card(410L))))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.NOT_FOUND);
    verifyNoInteractions(coverLetterQuestionCardRepository);
  }

  @Test
  @DisplayName("37번 저장: 빈칸 초안(추천 카드 없음)이면 연결을 지우기만 하고 새로 넣지 않는다")
  @SuppressWarnings("unchecked")
  void save_noCards_onlyClearsLinks() {
    when(coverLetterQuestionRepository.findByIdAndUserId(55L, 1L))
        .thenReturn(Optional.of(editedQuestion()));

    answerDraftWriter.save(1L, 55L, DRAFT, List.of());

    verify(coverLetterQuestionCardRepository).deleteAllByQuestionId(55L);
    ArgumentCaptor<List<CoverLetterQuestionCard>> links = ArgumentCaptor.forClass(List.class);
    verify(coverLetterQuestionCardRepository).saveAll(links.capture());
    assertThat(links.getValue()).isEmpty();
    verify(projectService).names(Set.of());
  }

  @Test
  @DisplayName("37번 저장: 초안의 빠진 역량 ID로 이름을 읽어 응답에 넘긴다 (M-13)")
  void save_passesMissingCompetencyNames() {
    CoverLetterQuestion question = editedQuestion();
    when(coverLetterQuestionRepository.findByIdAndUserId(55L, 1L))
        .thenReturn(Optional.of(question));
    AnswerDraft blankDraft = new AnswerDraft(List.of(), List.of(), List.of(4L), true);
    Map<Long, String> names = Map.of(4L, "갈등 조율");
    when(competencyService.names(List.of(4L))).thenReturn(names);

    answerDraftWriter.save(1L, 55L, blankDraft, List.of());

    verify(answerAssembler).assemble(eq(question), anyMap(), anyMap(), anyMap(), eq(names));
  }
}
