package com.sajarok.domain.matching.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.sajarok.domain.attachment.dto.AttachmentOwner;
import com.sajarok.domain.attachment.dto.AttachmentView;
import com.sajarok.domain.attachment.entity.AttachmentCategory;
import com.sajarok.domain.attachment.entity.AttachmentType;
import com.sajarok.domain.attachment.entity.ExtractStatus;
import com.sajarok.domain.attachment.service.AttachmentService;
import com.sajarok.domain.competency.service.CompetencyService;
import com.sajarok.domain.matching.dto.AnswerResponse;
import com.sajarok.domain.matching.dto.JobPostingDetailResponse;
import com.sajarok.domain.matching.entity.AnswerDraft;
import com.sajarok.domain.matching.entity.CoverLetterCardRole;
import com.sajarok.domain.matching.entity.CoverLetterQuestion;
import com.sajarok.domain.matching.entity.CoverLetterQuestionCard;
import com.sajarok.domain.matching.entity.JobPosting;
import com.sajarok.domain.matching.entity.PostingCompetency;
import com.sajarok.domain.matching.repository.CoverLetterQuestionCardRepository;
import com.sajarok.domain.matching.repository.CoverLetterQuestionRepository;
import com.sajarok.domain.matching.repository.JobPostingRepository;
import com.sajarok.domain.project.service.ProjectService;
import com.sajarok.domain.storycard.dto.CardSnapshot;
import com.sajarok.domain.storycard.entity.StoryCardType;
import com.sajarok.domain.storycard.service.StoryCardService;
import com.sajarok.domain.week.service.WeekService;
import com.sajarok.global.exception.BusinessException;
import com.sajarok.global.exception.ErrorCode;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class JobPostingServiceTest {

  @Mock private JobPostingRepository jobPostingRepository;
  @Mock private CoverLetterQuestionRepository coverLetterQuestionRepository;
  @Mock private CoverLetterQuestionCardRepository coverLetterQuestionCardRepository;
  @Mock private StoryCardService storyCardService;
  @Mock private ProjectService projectService;
  @Mock private AnswerAssembler answerAssembler;
  @Mock private AttachmentService attachmentService;
  @Mock private CompetencyService competencyService;
  @Mock private WeekService weekService;

  @InjectMocks private JobPostingService jobPostingService;

  private static CoverLetterQuestion question(Long id, int seq) {
    CoverLetterQuestion q = CoverLetterQuestion.builder().seq(seq).content("문항 " + seq).build();
    ReflectionTestUtils.setField(q, "id", id);
    return q;
  }

  @Test
  @DisplayName("39번: 문항 순서대로, 카드 연결은 모든 문항을 한 번에 읽어 문항별 역할을 넘기고, 역량에 카드 수를, 공고 첨부를 붙인다")
  void getDetail_loadsOnceAndAssembles() {
    JobPosting posting = JobPosting.builder().userId(1L).company("카카오").build();
    ReflectionTestUtils.setField(posting, "id", 21L);
    ReflectionTestUtils.setField(
        posting,
        "competencies",
        List.of(new PostingCompetency(3L, "협업을 중시"), new PostingCompetency(7L, "근거")));
    when(jobPostingRepository.findByIdAndUserId(21L, 1L)).thenReturn(Optional.of(posting));
    CoverLetterQuestion q1 = question(55L, 1);
    CoverLetterQuestion q2 = question(56L, 2);
    when(coverLetterQuestionRepository.findAllByJobPostingIdOrderBySeqAsc(21L))
        .thenReturn(List.of(q1, q2));
    when(coverLetterQuestionCardRepository.findAllByIdCoverLetterQuestionIdIn(List.of(55L, 56L)))
        .thenReturn(List.of(new CoverLetterQuestionCard(55L, 410L, CoverLetterCardRole.MAIN)));
    when(storyCardService.findPublished(1L)).thenReturn(List.of());
    when(storyCardService.countByCompetency(1L)).thenReturn(Map.of(3L, 2L));
    when(competencyService.names(List.of(3L, 7L))).thenReturn(Map.of(3L, "협업", 7L, "API 설계"));
    when(competencyService.names(Set.of())).thenReturn(Map.of());
    AnswerResponse answer =
        new AnswerResponse(55L, List.of(), 0, 0, List.of(), false, List.of(), false);
    when(answerAssembler.assemble(
            eq(q1),
            eq(Map.of(410L, CoverLetterCardRole.MAIN)),
            anyMap(),
            anyMap(),
            anyMap(),
            anyMap()))
        .thenReturn(answer);

    AttachmentView postingFile =
        new AttachmentView(
            701L,
            AttachmentType.FILE,
            AttachmentCategory.JOB_POSTING,
            "카카오 공고.pdf",
            null,
            null,
            "application/pdf",
            10L,
            ExtractStatus.DONE);
    when(attachmentService.findAll(AttachmentOwner.jobPosting(21L)))
        .thenReturn(List.of(postingFile));

    JobPostingDetailResponse response = jobPostingService.getDetail(1L, 21L);

    assertThat(response.company()).isEqualTo("카카오");
    assertThat(response.attachments()).containsExactly(postingFile);
    assertThat(response.questions())
        .extracting(JobPostingDetailResponse.QuestionItem::questionId)
        .containsExactly(55L, 56L);
    assertThat(response.questions().get(0).answer()).isSameAs(answer);
    assertThat(response.questions().get(1).answer()).isNull();
    assertThat(response.competencies())
        .extracting(JobPostingDetailResponse.PostingCompetencyItem::cardCount)
        .containsExactly(2L, 0L);
    verify(competencyService).names(List.of(3L, 7L));
    assertThat(response.competencies())
        .extracting(JobPostingDetailResponse.PostingCompetencyItem::name)
        .containsExactly("협업", "API 설계");
    verify(coverLetterQuestionCardRepository).findAllByIdCoverLetterQuestionIdIn(List.of(55L, 56L));
    verify(storyCardService).findPublished(1L);
  }

  @Test
  @DisplayName("39번: 모든 문항의 빠진 역량을 모아 이름을 한 번만 읽고, 문항마다 그 이름표를 넘긴다")
  void getDetail_loadsMissingCompetencyNamesOnce() {
    JobPosting posting = JobPosting.builder().userId(1L).company("카카오").build();
    ReflectionTestUtils.setField(posting, "id", 21L);
    when(jobPostingRepository.findByIdAndUserId(21L, 1L)).thenReturn(Optional.of(posting));
    CoverLetterQuestion q1 = question(55L, 1);
    CoverLetterQuestion q2 = question(56L, 2);
    ReflectionTestUtils.setField(q1, "answerDraft", draftMissing(4L));
    ReflectionTestUtils.setField(q2, "answerDraft", draftMissing(4L, 9L));
    when(coverLetterQuestionRepository.findAllByJobPostingIdOrderBySeqAsc(21L))
        .thenReturn(List.of(q1, q2));
    Map<Long, String> names = Map.of(4L, "갈등 조율", 9L, "문제 정의");
    when(competencyService.names(Set.of(4L, 9L))).thenReturn(names);

    jobPostingService.getDetail(1L, 21L);

    verify(competencyService).names(Set.of(4L, 9L));
    verify(answerAssembler).assemble(eq(q1), anyMap(), anyMap(), anyMap(), anyMap(), eq(names));
    verify(answerAssembler).assemble(eq(q2), anyMap(), anyMap(), anyMap(), anyMap(), eq(names));
  }

  @Test
  @DisplayName("39번: 공개 카드 중 회고 카드의 주차 ID를 모아 주차 번호를 한 번만 읽고, 문항마다 그 이름표를 넘긴다")
  void getDetail_loadsWeekNumbersOnce() {
    JobPosting posting = JobPosting.builder().userId(1L).company("카카오").build();
    ReflectionTestUtils.setField(posting, "id", 21L);
    when(jobPostingRepository.findByIdAndUserId(21L, 1L)).thenReturn(Optional.of(posting));
    CoverLetterQuestion q1 = question(55L, 1);
    CoverLetterQuestion q2 = question(56L, 2);
    when(coverLetterQuestionRepository.findAllByJobPostingIdOrderBySeqAsc(21L))
        .thenReturn(List.of(q1, q2));
    when(storyCardService.findPublished(1L))
        .thenReturn(
            List.of(
                snapshot(500L, StoryCardType.SESSION, 10L, null),
                snapshot(501L, StoryCardType.ASSIGNMENT, 11L, null),
                snapshot(410L, StoryCardType.PROJECT, null, 3L)));
    Map<Long, Integer> weekNumbers = Map.of(10L, 7, 11L, 8);
    when(weekService.findWeekNumbers(Set.of(10L, 11L))).thenReturn(weekNumbers);

    jobPostingService.getDetail(1L, 21L);

    verify(weekService).findWeekNumbers(Set.of(10L, 11L));
    verify(answerAssembler)
        .assemble(eq(q1), anyMap(), anyMap(), anyMap(), eq(weekNumbers), anyMap());
    verify(answerAssembler)
        .assemble(eq(q2), anyMap(), anyMap(), anyMap(), eq(weekNumbers), anyMap());
  }

  private static CardSnapshot snapshot(Long id, StoryCardType type, Long weekId, Long projectId) {
    return new CardSnapshot(
        id, type, weekId, projectId, "카드 " + id, null, null, null, null, null, Set.of());
  }

  private static AnswerDraft draftMissing(Long... competencyIds) {
    return new AnswerDraft(List.of(), List.of(), List.of(competencyIds), true);
  }

  @Test
  @DisplayName("39번: 남의 공고거나 없으면 NOT_FOUND이고 다른 것은 읽지 않는다")
  void getDetail_notMine_throwsNotFound() {
    when(jobPostingRepository.findByIdAndUserId(21L, 1L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> jobPostingService.getDetail(1L, 21L))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.NOT_FOUND);
    verifyNoInteractions(coverLetterQuestionRepository, storyCardService, attachmentService);
  }
}
