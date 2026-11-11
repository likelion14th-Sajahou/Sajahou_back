package com.sajarok.domain.matching.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.sajarok.domain.matching.ai.MatchingPrompts.PostingText;
import com.sajarok.domain.matching.entity.AnswerDraft;
import com.sajarok.domain.matching.entity.AnswerDraft.Segment;
import com.sajarok.domain.matching.entity.CoverLetterQuestion;
import com.sajarok.domain.matching.entity.JobPosting;
import com.sajarok.domain.matching.repository.CoverLetterQuestionRepository;
import com.sajarok.domain.matching.service.CardRecommender.Recommendation;
import com.sajarok.domain.storycard.dto.CardSnapshot;
import com.sajarok.domain.storycard.entity.StoryCardType;
import com.sajarok.domain.storycard.service.StoryCardService;
import com.sajarok.global.exception.BusinessException;
import com.sajarok.global.exception.ErrorCode;
import com.sajarok.infra.ai.AiLock;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 37번 문항별 답 초안 서비스 테스트. 카드 추천은 진짜 CardRecommender, AI(AnswerDrafter)와 저장(AnswerDraftWriter)은 가짜.
 */
@ExtendWith(MockitoExtension.class)
class CoverLetterQuestionServiceDraftTest {

  @Mock private CoverLetterQuestionRepository coverLetterQuestionRepository;
  @Mock private StoryCardService storyCardService;
  @Spy private CardRecommender cardRecommender = new CardRecommender();
  @Mock private AnswerDrafter answerDrafter;
  @Mock private AnswerDraftWriter answerDraftWriter;
  @Spy private AiLock aiLock = new AiLock();

  @InjectMocks private CoverLetterQuestionService coverLetterQuestionService;

  private static CoverLetterQuestion question(List<Long> competencyIds) {
    JobPosting posting =
        JobPosting.builder().userId(1L).company("카카오").department("백엔드").jd("대규모 트래픽").build();
    CoverLetterQuestion q =
        CoverLetterQuestion.builder()
            .jobPosting(posting)
            .seq(1)
            .content("협업 경험을 써 주세요")
            .charLimit(700)
            .build();
    ReflectionTestUtils.setField(q, "id", 55L);
    ReflectionTestUtils.setField(q, "competencyIds", competencyIds); // 36번(PR #19)이 저장하는 값
    return q;
  }

  private static CardSnapshot card(Long id, Long... tags) {
    return new CardSnapshot(
        id,
        StoryCardType.PROJECT,
        null,
        3L,
        "카드 " + id,
        null,
        null,
        null,
        null,
        null,
        Set.of(tags));
  }

  private static AnswerDraft aiDraft() {
    return new AnswerDraft(
        List.of(new Segment("text", "중커톤에서 팀 API 연동을 맡았습니다.", 1, null, null)),
        List.of(),
        List.of(),
        false);
  }

  @Test
  @DisplayName("37번: 남의 문항이거나 없으면 NOT_FOUND, 카드·AI·저장을 하나도 부르지 않는다")
  void createDraft_notMine_throwsNotFound() {
    when(coverLetterQuestionRepository.findWithPostingByIdAndUserId(55L, 1L))
        .thenReturn(Optional.empty());

    assertThatThrownBy(() -> coverLetterQuestionService.createDraft(1L, 55L))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.NOT_FOUND);
    verifyNoInteractions(storyCardService, answerDrafter, answerDraftWriter);
  }

  @Test
  @DisplayName("37번: 맞는 카드가 없으면 AI를 부르지 않고, 출처 없는 빈칸 4개 초안을 noMatchingCard true로 저장한다")
  void createDraft_noMatchingCard_savesBlankDraftWithoutAi() {
    when(coverLetterQuestionRepository.findWithPostingByIdAndUserId(55L, 1L))
        .thenReturn(Optional.of(question(List.of(3L))));
    when(storyCardService.findPublished(1L)).thenReturn(List.of(card(410L, 7L)));

    coverLetterQuestionService.createDraft(1L, 55L);

    ArgumentCaptor<AnswerDraft> captor = ArgumentCaptor.forClass(AnswerDraft.class);
    verify(answerDraftWriter).save(eq(1L), eq(55L), captor.capture(), eq(List.of()));
    AnswerDraft saved = captor.getValue();
    assertThat(saved.noMatchingCard()).isTrue();
    assertThat(saved.sources()).isEmpty();
    assertThat(saved.missingCompetencyIds()).containsExactly(3L);
    assertThat(saved.segments())
        .extracting(Segment::slot)
        .containsExactly("situation", "action", "reason", "result");
    verifyNoInteractions(answerDrafter);
  }

  @Test
  @DisplayName("37번: 맞는 카드가 있으면 추천 결과·문항·공고로 AI 초안을 만들고, 추천 카드(MAIN·SUB 순)와 함께 저장한다")
  void createDraft_withCards_draftsWithAiAndSaves() {
    CardSnapshot main = card(410L, 3L, 4L);
    CardSnapshot sub = card(300L, 3L);
    when(coverLetterQuestionRepository.findWithPostingByIdAndUserId(55L, 1L))
        .thenReturn(Optional.of(question(List.of(3L, 4L))));
    when(storyCardService.findPublished(1L)).thenReturn(List.of(sub, main));
    AnswerDraft draft = aiDraft();
    when(answerDrafter.draft(
            eq("협업 경험을 써 주세요"), eq(700), any(Recommendation.class), anyMap(), any()))
        .thenReturn(draft);

    coverLetterQuestionService.createDraft(1L, 55L);

    ArgumentCaptor<PostingText> posting = ArgumentCaptor.forClass(PostingText.class);
    verify(answerDrafter).draft(any(), any(), any(), anyMap(), posting.capture());
    assertThat(posting.getValue().company()).isEqualTo("카카오");
    assertThat(posting.getValue().jd()).isEqualTo("대규모 트래픽");
    verify(answerDraftWriter).save(1L, 55L, draft, List.of(main, sub));
  }

  @ParameterizedTest
  @EnumSource(
      value = ErrorCode.class,
      names = {"AI_FAILED", "AI_TIMEOUT"})
  @DisplayName("37번: AI가 실패하거나 시간 초과면 같은 에러를 돌려주고 아무것도 저장하지 않는다 (기존 초안 유지)")
  void createDraft_aiFails_savesNothing(ErrorCode code) {
    when(coverLetterQuestionRepository.findWithPostingByIdAndUserId(55L, 1L))
        .thenReturn(Optional.of(question(List.of(3L))));
    when(storyCardService.findPublished(1L)).thenReturn(List.of(card(410L, 3L)));
    when(answerDrafter.draft(any(), any(), any(), anyMap(), any()))
        .thenThrow(new BusinessException(code));

    assertThatThrownBy(() -> coverLetterQuestionService.createDraft(1L, 55L))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(code);
    verify(answerDraftWriter, never()).save(anyLong(), anyLong(), any(), anyList());
  }

  @Test
  @DisplayName("37번: 같은 문항에서 AI가 돌고 있으면(키 answer:{id}) AI_IN_PROGRESS이고 문항도 읽지 않는다")
  void createDraft_inProgress_throwsAiInProgress() {
    assertThatThrownBy(
            () -> aiLock.run("answer:55", () -> coverLetterQuestionService.createDraft(1L, 55L)))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.AI_IN_PROGRESS);
    verifyNoInteractions(coverLetterQuestionRepository, answerDrafter);
  }
}
