package com.sajarok.domain.matching.service;

import com.sajarok.domain.attachment.dto.AttachmentOwner;
import com.sajarok.domain.attachment.entity.Attachment;
import com.sajarok.domain.attachment.entity.ExtractStatus;
import com.sajarok.domain.attachment.service.AttachmentService;
import com.sajarok.domain.matching.ai.MatchingPrompts.CompetencyOption;
import com.sajarok.domain.matching.ai.MatchingPrompts.PostingText;
import com.sajarok.domain.matching.ai.PostingAnalysisResult;
import com.sajarok.domain.matching.dto.JobPostingCreateRequest;
import com.sajarok.domain.matching.dto.JobPostingCreateRequest.QuestionRequest;
import com.sajarok.domain.matching.dto.JobPostingCreateResponse;
import com.sajarok.domain.matching.dto.JobPostingDetailResponse;
import com.sajarok.domain.matching.dto.JobPostingDetailResponse.PostingCompetencyItem;
import com.sajarok.domain.matching.dto.JobPostingDetailResponse.QuestionItem;
import com.sajarok.domain.matching.entity.CoverLetterCardRole;
import com.sajarok.domain.matching.entity.CoverLetterQuestion;
import com.sajarok.domain.matching.entity.CoverLetterQuestionCard;
import com.sajarok.domain.matching.entity.JobPosting;
import com.sajarok.domain.matching.repository.CoverLetterQuestionCardRepository;
import com.sajarok.domain.matching.repository.CoverLetterQuestionRepository;
import com.sajarok.domain.matching.repository.JobPostingRepository;
import com.sajarok.domain.project.service.ProjectService;
import com.sajarok.domain.storycard.dto.CardSnapshot;
import com.sajarok.domain.storycard.service.StoryCardService;
import com.sajarok.global.exception.BusinessException;
import com.sajarok.global.exception.ErrorCode;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class JobPostingService {

  private final JobPostingRepository jobPostingRepository;
  private final CoverLetterQuestionRepository coverLetterQuestionRepository;
  private final CoverLetterQuestionCardRepository coverLetterQuestionCardRepository;
  private final StoryCardService storyCardService;
  private final ProjectService projectService;
  private final AnswerAssembler answerAssembler;
  private final JobPostingWriter jobPostingWriter;
  private final PostingAnalyzer postingAnalyzer;
  private final AttachmentService attachmentService;

  /**
   * 36번: 공고·문항을 저장하고 AI로 공고가 원하는 역량과 문항별 역량을 찾는다.
   *
   * <p>AI는 최대 50초 걸려서 트랜잭션 밖에서 부른다(DB 연결을 붙잡지 않게). 그래서 이 메서드는 트랜잭션 없이(NOT_SUPPORTED) 돌고, DB 작업은
   * JobPostingWriter의 짧은 트랜잭션으로 나눈다: ① 공고·문항 저장 → AI 분석 → ② 역량 저장. AI 분석이 실패하면(AI_FAILED·AI_TIMEOUT
   * 등) ①에서 저장한 공고를 지우고 같은 에러를 돌려준다.
   *
   * <p>공고 4칸과 첨부가 모두 비면 EMPTY_MATERIAL(AI가 읽을 자료가 없음). 첨부 글자는 공고를 저장하기 전에 읽어서, 남의 첨부(NOT_FOUND)나 읽는
   * 중(ATTACHMENT_PENDING)이면 공고를 만들지 않고 끝낸다.
   */
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  public JobPostingCreateResponse create(Long userId, JobPostingCreateRequest request) {
    if (!request.hasMaterial()) {
      throw new BusinessException(ErrorCode.EMPTY_MATERIAL);
    }
    List<String> attachmentTexts = readAttachmentTexts(userId, request.attachmentIds());
    JobPosting posting = jobPostingWriter.save(userId, request);

    JobPostingWriter.Applied applied;
    try {
      applied =
          jobPostingWriter.applyAnalysis(
              userId,
              posting.getId(),
              request.attachmentIds(),
              analyze(userId, request, attachmentTexts));
    } catch (RuntimeException e) {
      discardQuietly(posting.getId(), e);
      throw e;
    }
    return toCreateResponse(applied, storyCardService.countByCompetency(userId));
  }

  /**
   * 아직 공고에 연결하지 않은 첨부를 ID마다 읽는다(내 첨부가 아니면 NOT_FOUND). 추출 DONE인 글자만 쓰고 FAILED·SKIPPED는 건너뛴다.
   *
   * <p>TODO(첨부 담당): ID 목록으로 읽는 readTexts가 생기면 바꾼다. 지금은 readTexts처럼 기다리지 않고 바로 ATTACHMENT_PENDING.
   */
  private List<String> readAttachmentTexts(Long userId, List<Long> attachmentIds) {
    if (attachmentIds == null || attachmentIds.isEmpty()) {
      return List.of();
    }
    List<Attachment> attachments =
        new LinkedHashSet<>(attachmentIds)
            .stream().map(id -> attachmentService.getOwnedAttachment(userId, id)).toList();
    if (attachments.stream().anyMatch(a -> a.getExtractStatus() == ExtractStatus.PENDING)) {
      throw new BusinessException(ErrorCode.ATTACHMENT_PENDING);
    }
    return attachments.stream()
        .filter(a -> a.getExtractStatus() == ExtractStatus.DONE)
        .map(Attachment::getExtractedText)
        .toList();
  }

  private PostingAnalysisResult analyze(
      Long userId, JobPostingCreateRequest request, List<String> attachmentTexts) {
    // TODO(역량 담당): competencyService.findAvailable(userId)로 내 파트의 역량 목록을 받는다.
    List<CompetencyOption> competencies = List.of();
    PostingText text =
        new PostingText(
            request.company(),
            request.department(),
            request.postingText(),
            request.jd(),
            request.preferredQualification(),
            request.companyInfo());
    List<String> contents = request.questions().stream().map(QuestionRequest::content).toList();
    return postingAnalyzer.analyze(text, attachmentTexts, competencies, contents);
  }

  /** 공고 삭제까지 실패해도 원래 에러(AI_FAILED 등)를 돌려주도록, 삭제 실패는 로그와 suppressed로만 남긴다. */
  private void discardQuietly(Long jobPostingId, RuntimeException cause) {
    try {
      jobPostingWriter.discard(jobPostingId);
    } catch (RuntimeException discardFailure) {
      log.error("36번 AI 실패 후 공고 삭제 실패 jobPostingId={}", jobPostingId, discardFailure);
      cause.addSuppressed(discardFailure);
    }
  }

  private static JobPostingCreateResponse toCreateResponse(
      JobPostingWriter.Applied applied, Map<Long, Long> cardCounts) {
    JobPosting posting = applied.posting();
    List<PostingCompetencyItem> competencies =
        posting.getCompetencies() == null
            ? List.of()
            : posting.getCompetencies().stream()
                .map(
                    c ->
                        new PostingCompetencyItem(
                            c.competencyId(),
                            // TODO(역량 담당): competencyService.names로 이름을 채운다. 지금은 null.
                            null,
                            c.evidenceQuote(),
                            cardCounts.getOrDefault(c.competencyId(), 0L)))
                .toList();
    List<JobPostingCreateResponse.QuestionItem> questionItems =
        applied.questions().stream()
            .map(
                q ->
                    new JobPostingCreateResponse.QuestionItem(
                        q.getId(),
                        q.getSeq(),
                        q.getContent(),
                        q.getCharLimit(),
                        q.getCompetencyIds()))
            .toList();
    return new JobPostingCreateResponse(
        posting.getId(), competencies, competencies.isEmpty(), questionItems);
  }

  /**
   * 39번: 매칭 결과(공고 상세). 남의 공고거나 없으면 NOT_FOUND.
   *
   * <p>문항·카드 연결·공개 카드·프로젝트 이름·역량별 카드 수를 각각 한 번씩만 읽는다 (문항 수만큼 늘지 않음, N+1 방지).
   */
  public JobPostingDetailResponse getDetail(Long userId, Long jobPostingId) {
    JobPosting posting =
        jobPostingRepository
            .findByIdAndUserId(jobPostingId, userId)
            .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));

    List<CoverLetterQuestion> questions =
        coverLetterQuestionRepository.findAllByJobPostingIdOrderBySeqAsc(jobPostingId);
    Map<Long, Map<Long, CoverLetterCardRole>> rolesByQuestion = loadRoles(questions);
    Map<Long, CardSnapshot> liveCards =
        AnswerAssembler.byId(storyCardService.findPublished(userId));
    Map<Long, String> projectNames =
        projectService.names(
            liveCards.values().stream()
                .map(CardSnapshot::projectId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet()));
    Map<Long, Long> cardCounts = storyCardService.countByCompetency(userId);

    List<QuestionItem> questionItems =
        questions.stream()
            .map(
                q ->
                    new QuestionItem(
                        q.getId(),
                        q.getSeq(),
                        q.getContent(),
                        q.getCharLimit(),
                        q.getCompetencyIds() == null ? List.of() : q.getCompetencyIds(),
                        answerAssembler.assemble(
                            q,
                            rolesByQuestion.getOrDefault(q.getId(), Map.of()),
                            liveCards,
                            projectNames),
                        q.getEditedAnswer()))
            .toList();

    List<PostingCompetencyItem> competencies =
        posting.getCompetencies() == null
            ? List.of()
            : posting.getCompetencies().stream()
                .map(
                    c ->
                        new PostingCompetencyItem(
                            c.competencyId(),
                            // TODO(역량 담당): competencyService.names로 이름을 채운다. 지금은 null.
                            null,
                            c.evidenceQuote(),
                            cardCounts.getOrDefault(c.competencyId(), 0L)))
                .toList();

    return new JobPostingDetailResponse(
        posting.getId(),
        posting.getCompany(),
        posting.getDepartment(),
        posting.getPostingText(),
        posting.getJd(),
        posting.getPreferredQualification(),
        posting.getCompanyInfo(),
        attachmentService.findAll(AttachmentOwner.jobPosting(posting.getId())),
        competencies,
        questionItems);
  }

  /** 문항 ID → (카드 ID → 역할). 모든 문항을 쿼리 한 번으로. */
  private Map<Long, Map<Long, CoverLetterCardRole>> loadRoles(List<CoverLetterQuestion> questions) {
    if (questions.isEmpty()) {
      return Map.of();
    }
    return coverLetterQuestionCardRepository
        .findAllByIdCoverLetterQuestionIdIn(
            questions.stream().map(CoverLetterQuestion::getId).toList())
        .stream()
        .collect(
            Collectors.groupingBy(
                link -> link.getId().getCoverLetterQuestionId(),
                Collectors.toMap(
                    link -> link.getId().getCardId(), CoverLetterQuestionCard::getRole)));
  }
}
