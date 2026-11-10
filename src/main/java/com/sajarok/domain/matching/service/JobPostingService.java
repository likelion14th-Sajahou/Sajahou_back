package com.sajarok.domain.matching.service;

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
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
        // TODO(첨부 담당): attachmentService.findAll(공고)로 채운다.
        List.of(),
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
