package com.sajarok.domain.matching.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sajarok.domain.matching.ai.PostingAnalysisResult;
import com.sajarok.domain.matching.dto.JobPostingCreateRequest;
import com.sajarok.domain.matching.dto.JobPostingCreateRequest.QuestionRequest;
import com.sajarok.domain.matching.entity.CoverLetterQuestion;
import com.sajarok.domain.matching.entity.JobPosting;
import com.sajarok.domain.matching.entity.PostingCompetency;
import com.sajarok.domain.matching.repository.CoverLetterQuestionRepository;
import com.sajarok.domain.matching.repository.JobPostingRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** 36번 DB 작업 테스트: 문항 순서, AI 결과 반영(중복 제거), 실패 때 삭제 순서. */
@ExtendWith(MockitoExtension.class)
class JobPostingWriterTest {

  @Mock private JobPostingRepository jobPostingRepository;
  @Mock private CoverLetterQuestionRepository coverLetterQuestionRepository;

  @InjectMocks private JobPostingWriter jobPostingWriter;

  private static CoverLetterQuestion question(JobPosting posting, int seq) {
    return CoverLetterQuestion.builder().jobPosting(posting).seq(seq).content("문항 " + seq).build();
  }

  @Test
  @DisplayName("36번 저장: 공고를 내 회원 ID로 저장하고, 문항 seq를 보낸 순서대로 1부터 매긴다")
  @SuppressWarnings("unchecked")
  void save_numbersQuestionsInOrder() {
    when(jobPostingRepository.save(any(JobPosting.class))).thenAnswer(i -> i.getArgument(0));
    JobPostingCreateRequest request =
        new JobPostingCreateRequest(
            "카카오",
            null,
            null,
            "대규모 트래픽",
            null,
            null,
            null,
            List.of(new QuestionRequest("협업 경험", 700), new QuestionRequest("지원 동기", null)));

    JobPosting saved = jobPostingWriter.save(1L, request);

    ArgumentCaptor<List<CoverLetterQuestion>> captor = ArgumentCaptor.forClass(List.class);
    verify(coverLetterQuestionRepository).saveAll(captor.capture());
    List<CoverLetterQuestion> questions = captor.getValue();
    assertThat(saved.getUserId()).isEqualTo(1L);
    assertThat(saved.getCompetencies()).isNull();
    assertThat(questions).extracting(CoverLetterQuestion::getSeq).containsExactly(1, 2);
    assertThat(questions)
        .extracting(CoverLetterQuestion::getContent)
        .containsExactly("협업 경험", "지원 동기");
    assertThat(questions).extracting(CoverLetterQuestion::getCharLimit).containsExactly(700, null);
    assertThat(questions).allMatch(q -> q.getJobPosting() == saved);
  }

  @Test
  @DisplayName("36번 역량 반영: 같은 역량은 하나로 합치고, AI 결과에 없는 문항은 빈 배열([])로 저장한다")
  void applyAnalysis_dedupesAndFillsMissingQuestions() {
    JobPosting posting = JobPosting.builder().userId(1L).build();
    List<CoverLetterQuestion> questions = List.of(question(posting, 1), question(posting, 2));
    when(jobPostingRepository.findById(21L)).thenReturn(Optional.of(posting));
    when(coverLetterQuestionRepository.findAllByJobPostingIdOrderBySeqAsc(21L))
        .thenReturn(questions);
    PostingAnalysisResult result =
        new PostingAnalysisResult(
            List.of(
                new PostingAnalysisResult.Competency(3L, "협업"),
                new PostingAnalysisResult.Competency(3L, "중복"),
                new PostingAnalysisResult.Competency(7L, "문제 해결")),
            List.of(new PostingAnalysisResult.Question(1, List.of(3L, 3L, 7L))));

    JobPostingWriter.Applied applied = jobPostingWriter.applyAnalysis(21L, result);

    assertThat(posting.getCompetencies())
        .containsExactly(new PostingCompetency(3L, "협업"), new PostingCompetency(7L, "문제 해결"));
    assertThat(questions.get(0).getCompetencyIds()).containsExactly(3L, 7L);
    assertThat(questions.get(1).getCompetencyIds()).isEmpty();
    assertThat(applied.posting()).isSameAs(posting);
    assertThat(applied.questions()).isEqualTo(questions);
  }

  @Test
  @DisplayName("36번 실패 처리: 문항 → 공고 순서로 지운다 (FK에 막히지 않게)")
  void discard_deletesQuestionsThenPosting() {
    jobPostingWriter.discard(21L);

    InOrder order = inOrder(coverLetterQuestionRepository, jobPostingRepository);
    order.verify(coverLetterQuestionRepository).deleteAllByJobPostingId(21L);
    order.verify(jobPostingRepository).deleteById(21L);
  }
}
