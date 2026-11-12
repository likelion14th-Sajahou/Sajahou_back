package com.sajarok.domain.matching.service;

import com.sajarok.domain.matching.ai.MatchingPrompts.PostingText;
import com.sajarok.domain.matching.dto.AnswerResponse;
import com.sajarok.domain.matching.dto.EditedAnswerResponse;
import com.sajarok.domain.matching.entity.AnswerDraft;
import com.sajarok.domain.matching.entity.CoverLetterQuestion;
import com.sajarok.domain.matching.entity.JobPosting;
import com.sajarok.domain.matching.repository.CoverLetterQuestionRepository;
import com.sajarok.domain.matching.service.CardRecommender.Recommendation;
import com.sajarok.domain.storycard.dto.CardSnapshot;
import com.sajarok.domain.storycard.service.StoryCardService;
import com.sajarok.global.exception.BusinessException;
import com.sajarok.global.exception.ErrorCode;
import com.sajarok.infra.ai.AiLock;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CoverLetterQuestionService {

  private final CoverLetterQuestionRepository coverLetterQuestionRepository;
  private final StoryCardService storyCardService;
  private final CardRecommender cardRecommender;
  private final AnswerDrafter answerDrafter;
  private final AnswerDraftWriter answerDraftWriter;
  private final AiLock aiLock;

  /**
   * 37번: 문항별 답 초안. 다시 부르면 초안을 새로 만들고 직접 고친 답은 비운다.
   *
   * <p>① 문항·공고·공개 카드 읽기 → 카드 추천 → ② AI 초안(트랜잭션 밖, 최대 50초) → ③ 저장(AnswerDraftWriter 트랜잭션). AI가 실패하면
   * ③까지 가지 않아 기존 초안·카드 연결이 그대로 남는다.
   *
   * <p>맞는 카드가 하나도 없으면 AI를 부르지 않고 빈칸만 있는 초안을 저장한다(출처가 공고뿐이면 쓸 경험이 없어 지어낼 위험만 있음). 남의 문항이거나 없으면
   * NOT_FOUND.
   */
  public AnswerResponse createDraft(Long userId, Long questionId) {
    // 잠금 전에 소유부터 확인해 남의 ID로는 잠금을 잡지 않는다(주인 ID만 읽음).
    coverLetterQuestionRepository
        .findUserIdById(questionId)
        .filter(userId::equals)
        .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
    return aiLock.run("answer:" + questionId, () -> createDraftLocked(userId, questionId));
  }

  private AnswerResponse createDraftLocked(Long userId, Long questionId) {
    CoverLetterQuestion question =
        coverLetterQuestionRepository
            .findWithPostingByIdAndUserId(questionId, userId)
            .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
    List<CardSnapshot> cards = storyCardService.findPublished(userId);
    Recommendation recommendation = cardRecommender.recommend(question.getCompetencyIds(), cards);

    AnswerDraft draft =
        recommendation.noMatchingCard()
            ? AnswerDrafter.blankDraft(List.of(), recommendation.missingCompetencyIds(), true)
            : answerDrafter.draft(
                question.getContent(),
                question.getCharLimit(),
                recommendation,
                AnswerAssembler.byId(cards),
                postingText(question.getJobPosting()));

    return answerDraftWriter.save(userId, questionId, draft, recommendation.cards());
  }

  private static PostingText postingText(JobPosting posting) {
    return new PostingText(
        posting.getCompany(),
        posting.getDepartment(),
        posting.getPostingText(),
        posting.getJd(),
        posting.getPreferredQualification(),
        posting.getCompanyInfo());
  }

  /**
   * 38번: 직접 고친 답 저장. null이면 AI 초안으로 되돌린다.
   *
   * <p>남의 문항이거나 없으면 NOT_FOUND, 초안(37번)을 만들기 전이면 INVALID_STATE. 저장은 변경 감지로 한다.
   */
  @Transactional
  public EditedAnswerResponse editAnswer(Long userId, Long questionId, String editedAnswer) {
    CoverLetterQuestion question =
        coverLetterQuestionRepository
            .findByIdAndUserId(questionId, userId)
            .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
    if (question.getAnswerDraft() == null) {
      throw new BusinessException(ErrorCode.INVALID_STATE);
    }

    question.editAnswer(editedAnswer);
    return new EditedAnswerResponse(
        question.getId(), question.getEditedAnswer(), question.charCount());
  }
}
