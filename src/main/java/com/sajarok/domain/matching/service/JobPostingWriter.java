package com.sajarok.domain.matching.service;

import com.sajarok.domain.attachment.dto.AttachmentOwner;
import com.sajarok.domain.attachment.service.AttachmentService;
import com.sajarok.domain.matching.ai.PostingAnalysisResult;
import com.sajarok.domain.matching.dto.JobPostingCreateRequest;
import com.sajarok.domain.matching.dto.JobPostingCreateRequest.QuestionRequest;
import com.sajarok.domain.matching.entity.CoverLetterQuestion;
import com.sajarok.domain.matching.entity.JobPosting;
import com.sajarok.domain.matching.entity.PostingCompetency;
import com.sajarok.domain.matching.repository.CoverLetterQuestionRepository;
import com.sajarok.domain.matching.repository.JobPostingRepository;
import com.sajarok.global.exception.BusinessException;
import com.sajarok.global.exception.ErrorCode;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 36번의 DB 작업을 짧은 트랜잭션 여러 개로 나눈다: 저장 → (AI는 밖에서) → 역량 반영, 실패하면 버리기.
 *
 * <p>JobPostingService 안의 메서드끼리 부르면 @Transactional이 적용되지 않아서(같은 객체 안 호출은 프록시를 거치지 않음) 따로 둔다.
 */
@Component
@RequiredArgsConstructor
public class JobPostingWriter {

  private final JobPostingRepository jobPostingRepository;
  private final CoverLetterQuestionRepository coverLetterQuestionRepository;
  private final AttachmentService attachmentService;

  /** 역량을 반영한 공고와 문항(순서대로). */
  public record Applied(JobPosting posting, List<CoverLetterQuestion> questions) {}

  /** 공고와 문항을 저장한다. 문항 seq는 보낸 순서대로 1부터. */
  @Transactional
  public JobPosting save(Long userId, JobPostingCreateRequest request) {
    JobPosting posting =
        jobPostingRepository.save(
            JobPosting.builder()
                .userId(userId)
                .company(request.company())
                .department(request.department())
                .postingText(request.postingText())
                .jd(request.jd())
                .preferredQualification(request.preferredQualification())
                .companyInfo(request.companyInfo())
                .build());

    List<CoverLetterQuestion> questions = new ArrayList<>();
    List<QuestionRequest> requested = request.questions();
    for (int i = 0; i < requested.size(); i++) {
      questions.add(
          CoverLetterQuestion.builder()
              .jobPosting(posting)
              .seq(i + 1)
              .content(requested.get(i).content())
              .charLimit(requested.get(i).charLimit())
              .build());
    }
    coverLetterQuestionRepository.saveAll(questions);
    return posting;
  }

  /**
   * 검증한 AI 결과를 공고·문항에 저장하고, 첨부를 공고에 연결한 뒤 공고와 문항(순서대로)을 돌려준다. 같은 역량이 두 번 오면 처음 것만 남긴다. AI 결과에 없는
   * 문항은 역량 없음([])으로 저장한다.
   *
   * <p>첨부 연결을 AI 성공 뒤에 하는 이유: AI가 실패해 공고를 지울 때 연결된 첨부가 있으면 FK 때문에 삭제가 막힌다. 연결이 실패하면(이미 다른 곳에 연결 등)
   * 이 트랜잭션이 통째로 되돌아가고, 부르는 쪽이 공고를 지운다.
   */
  @Transactional
  public Applied applyAnalysis(
      Long userId, Long jobPostingId, List<Long> attachmentIds, PostingAnalysisResult result) {
    JobPosting posting =
        jobPostingRepository
            .findById(jobPostingId)
            .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
    Set<Long> seen = new HashSet<>();
    posting.applyCompetencies(
        result.competencies().stream()
            .filter(c -> seen.add(c.competencyId()))
            .map(c -> new PostingCompetency(c.competencyId(), c.evidenceQuote()))
            .toList());

    Map<Integer, List<Long>> idsBySeq =
        result.questions().stream()
            .collect(
                Collectors.toMap(
                    PostingAnalysisResult.Question::seq,
                    q -> q.competencyIds() == null ? List.of() : q.competencyIds(),
                    (first, second) -> first));
    List<CoverLetterQuestion> questions =
        coverLetterQuestionRepository.findAllByJobPostingIdOrderBySeqAsc(jobPostingId);
    questions.forEach(
        q ->
            q.applyCompetencyIds(
                idsBySeq.getOrDefault(q.getSeq(), List.of()).stream().distinct().toList()));
    attachmentService.connect(userId, attachmentIds, AttachmentOwner.jobPosting(jobPostingId));
    return new Applied(posting, questions);
  }

  /**
   * AI 분석이 실패한 공고를 지운다. FE는 실패 응답에서 공고 ID를 받지 못해 다시 제출하므로, 남겨 두면 쓸 수 없는 공고가 쌓인다. 문항 → 공고 순서로
   * 지운다(FK). 첨부는 applyAnalysis에서야 연결하므로 여기서 끊을 첨부는 없다.
   */
  @Transactional
  public void discard(Long jobPostingId) {
    coverLetterQuestionRepository.deleteAllByJobPostingId(jobPostingId);
    jobPostingRepository.deleteById(jobPostingId);
  }
}
