package com.sajarok.domain.matching.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.sajarok.domain.matching.ai.PostingAnalysisResult;
import com.sajarok.domain.matching.dto.JobPostingCreateRequest;
import com.sajarok.domain.matching.dto.JobPostingCreateRequest.QuestionRequest;
import com.sajarok.domain.matching.dto.JobPostingCreateResponse;
import com.sajarok.domain.matching.entity.CoverLetterQuestion;
import com.sajarok.domain.matching.entity.JobPosting;
import com.sajarok.domain.matching.entity.PostingCompetency;
import com.sajarok.domain.matching.repository.CoverLetterQuestionCardRepository;
import com.sajarok.domain.matching.repository.CoverLetterQuestionRepository;
import com.sajarok.domain.matching.repository.JobPostingRepository;
import com.sajarok.domain.project.service.ProjectService;
import com.sajarok.domain.storycard.service.StoryCardService;
import com.sajarok.global.exception.BusinessException;
import com.sajarok.global.exception.ErrorCode;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

/** 36번 공고 저장·역량 찾기 서비스 테스트. AI 분석(PostingAnalyzer)과 DB 작업(JobPostingWriter)은 가짜로 바꾼다. */
@ExtendWith(MockitoExtension.class)
class JobPostingServiceCreateTest {

  @Mock private JobPostingRepository jobPostingRepository;
  @Mock private CoverLetterQuestionRepository coverLetterQuestionRepository;
  @Mock private CoverLetterQuestionCardRepository coverLetterQuestionCardRepository;
  @Mock private StoryCardService storyCardService;
  @Mock private ProjectService projectService;
  @Mock private AnswerAssembler answerAssembler;
  @Mock private JobPostingWriter jobPostingWriter;
  @Mock private PostingAnalyzer postingAnalyzer;

  @InjectMocks private JobPostingService jobPostingService;

  private static JobPostingCreateRequest request(String jd, List<Long> attachmentIds) {
    return new JobPostingCreateRequest(
        "카카오",
        "백엔드",
        null,
        jd,
        null,
        null,
        attachmentIds,
        List.of(new QuestionRequest("협업 경험을 써 주세요", 700)));
  }

  private static JobPosting savedPosting() {
    JobPosting posting = JobPosting.builder().userId(1L).company("카카오").build();
    ReflectionTestUtils.setField(posting, "id", 21L);
    return posting;
  }

  private static CoverLetterQuestion question(JobPosting posting, List<Long> competencyIds) {
    CoverLetterQuestion q =
        CoverLetterQuestion.builder()
            .jobPosting(posting)
            .seq(1)
            .content("협업 경험을 써 주세요")
            .charLimit(700)
            .build();
    ReflectionTestUtils.setField(q, "id", 55L);
    q.applyCompetencyIds(competencyIds);
    return q;
  }

  @Test
  @DisplayName("36번: 공고 4칸과 첨부가 모두 비면 EMPTY_MATERIAL, 공고를 저장하지도 AI를 부르지도 않는다")
  void create_noMaterial_throwsEmptyMaterial() {
    assertThatThrownBy(() -> jobPostingService.create(1L, request("  ", List.of())))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.EMPTY_MATERIAL);
    verifyNoInteractions(jobPostingWriter, postingAnalyzer);
  }

  @Test
  @DisplayName("36번: 공고 칸이 비어도 첨부가 있으면 자료로 인정해 저장한다")
  void create_attachmentOnly_isMaterial() {
    JobPosting posting = savedPosting();
    posting.applyCompetencies(List.of());
    when(jobPostingWriter.save(eq(1L), any())).thenReturn(posting);
    when(postingAnalyzer.analyze(any(), anyList(), anyList()))
        .thenReturn(new PostingAnalysisResult(List.of(), List.of()));
    when(jobPostingWriter.applyAnalysis(eq(21L), any()))
        .thenReturn(new JobPostingWriter.Applied(posting, List.of(question(posting, List.of()))));

    jobPostingService.create(1L, request(null, List.of(701L)));

    verify(jobPostingWriter).save(eq(1L), any());
  }

  @Test
  @DisplayName("36번: 저장 → AI 분석 → 역량 저장 순서로 하고, 역량에 내 카드 수를 붙여 응답한다")
  void create_savesAnalyzesAndResponds() {
    JobPosting posting = savedPosting();
    posting.applyCompetencies(List.of(new PostingCompetency(3L, "다양한 직군과 협업한 경험")));
    PostingAnalysisResult result =
        new PostingAnalysisResult(
            List.of(new PostingAnalysisResult.Competency(3L, "다양한 직군과 협업한 경험")),
            List.of(new PostingAnalysisResult.Question(1, List.of(3L))));
    when(jobPostingWriter.save(eq(1L), any())).thenReturn(posting);
    when(postingAnalyzer.analyze(any(), anyList(), eq(List.of("협업 경험을 써 주세요")))).thenReturn(result);
    when(jobPostingWriter.applyAnalysis(21L, result))
        .thenReturn(new JobPostingWriter.Applied(posting, List.of(question(posting, List.of(3L)))));
    when(storyCardService.countByCompetency(1L)).thenReturn(Map.of(3L, 2L));

    JobPostingCreateResponse response = jobPostingService.create(1L, request("대규모 트래픽", null));

    InOrder order = inOrder(jobPostingWriter, postingAnalyzer);
    order.verify(jobPostingWriter).save(eq(1L), any());
    order.verify(postingAnalyzer).analyze(any(), anyList(), anyList());
    order.verify(jobPostingWriter).applyAnalysis(21L, result);
    assertThat(response.jobPostingId()).isEqualTo(21L);
    assertThat(response.noCompetencyFound()).isFalse();
    assertThat(response.competencies()).hasSize(1);
    assertThat(response.competencies().get(0).competencyId()).isEqualTo(3L);
    assertThat(response.competencies().get(0).evidenceQuote()).isEqualTo("다양한 직군과 협업한 경험");
    assertThat(response.competencies().get(0).cardCount()).isEqualTo(2L);
    assertThat(response.questions().get(0).questionId()).isEqualTo(55L);
    assertThat(response.questions().get(0).seq()).isEqualTo(1);
    assertThat(response.questions().get(0).competencyIds()).containsExactly(3L);
    verify(jobPostingWriter, never()).discard(anyLong());
  }

  @Test
  @DisplayName("36번: 역량을 하나도 못 찾으면 실패가 아니라 competencies 빈 배열, noCompetencyFound true")
  void create_noCompetency_flagsNoCompetencyFound() {
    JobPosting posting = savedPosting();
    posting.applyCompetencies(List.of());
    when(jobPostingWriter.save(eq(1L), any())).thenReturn(posting);
    when(postingAnalyzer.analyze(any(), anyList(), anyList()))
        .thenReturn(new PostingAnalysisResult(List.of(), List.of()));
    when(jobPostingWriter.applyAnalysis(eq(21L), any()))
        .thenReturn(new JobPostingWriter.Applied(posting, List.of(question(posting, List.of()))));

    JobPostingCreateResponse response = jobPostingService.create(1L, request("대규모 트래픽", null));

    assertThat(response.competencies()).isEmpty();
    assertThat(response.noCompetencyFound()).isTrue();
    assertThat(response.questions().get(0).competencyIds()).isEmpty();
  }

  @ParameterizedTest
  @EnumSource(
      value = ErrorCode.class,
      names = {"AI_FAILED", "AI_TIMEOUT"})
  @DisplayName("36번: AI가 실패하거나 시간 초과면 저장한 공고를 지우고 같은 에러를 돌려준다 (역량 저장은 안 함)")
  void create_aiFails_discardsPostingAndRethrows(ErrorCode code) {
    when(jobPostingWriter.save(eq(1L), any())).thenReturn(savedPosting());
    when(postingAnalyzer.analyze(any(), anyList(), anyList()))
        .thenThrow(new BusinessException(code));

    assertThatThrownBy(() -> jobPostingService.create(1L, request("대규모 트래픽", null)))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(code);
    verify(jobPostingWriter).discard(21L);
    verify(jobPostingWriter, never()).applyAnalysis(anyLong(), any());
  }

  @Test
  @DisplayName("36번: 공고 삭제까지 실패해도 원래 AI 에러를 돌려준다 (삭제 실패는 suppressed로 남김)")
  void create_discardAlsoFails_keepsOriginalError() {
    when(jobPostingWriter.save(eq(1L), any())).thenReturn(savedPosting());
    when(postingAnalyzer.analyze(any(), anyList(), anyList()))
        .thenThrow(new BusinessException(ErrorCode.AI_FAILED));
    doThrow(new IllegalStateException("DB 끊김")).when(jobPostingWriter).discard(21L);

    assertThatThrownBy(() -> jobPostingService.create(1L, request("대규모 트래픽", null)))
        .isInstanceOf(BusinessException.class)
        .satisfies(
            e -> {
              assertThat(((BusinessException) e).getErrorCode()).isEqualTo(ErrorCode.AI_FAILED);
              assertThat(e.getSuppressed()).hasSize(1);
            });
  }
}
