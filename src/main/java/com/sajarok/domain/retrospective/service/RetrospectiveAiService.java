package com.sajarok.domain.retrospective.service;

import com.sajarok.domain.attachment.dto.AttachmentOwner;
import com.sajarok.domain.attachment.entity.ExtractStatus;
import com.sajarok.domain.attachment.service.AttachmentService;
import com.sajarok.domain.question.dto.QuestionView;
import com.sajarok.domain.question.entity.QuestionOwner;
import com.sajarok.domain.question.entity.QuestionStatus;
import com.sajarok.domain.question.service.AiQuestionService;
import com.sajarok.domain.retrospective.ai.RetroAttachment;
import com.sajarok.domain.retrospective.ai.RetroQuestionResult.Validated;
import com.sajarok.domain.retrospective.dto.RetroQuestionMode;
import com.sajarok.domain.retrospective.dto.RetrospectiveQuestionsResponse;
import com.sajarok.domain.retrospective.dto.RetrospectiveReviewResponse;
import com.sajarok.domain.retrospective.entity.Retrospective;
import com.sajarok.domain.retrospective.repository.RetrospectiveRepository;
import com.sajarok.domain.storycard.dto.RetroCardDraft;
import com.sajarok.domain.storycard.service.StoryCardService;
import com.sajarok.domain.user.entity.Part;
import com.sajarok.domain.user.entity.User;
import com.sajarok.domain.user.repository.UserRepository;
import com.sajarok.domain.week.service.WeekService;
import com.sajarok.global.exception.BusinessException;
import com.sajarok.global.exception.ErrorCode;
import com.sajarok.infra.ai.AiLock;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RetrospectiveAiService {
  private final RetrospectiveRepository retrospectiveRepository;
  private final UserRepository userRepository; // 파트 조회용(읽기만). 나중에 지윤님 UserService 생기면 교체
  private final AttachmentService attachmentService;
  private final AiQuestionService aiQuestionService;
  private final RetroQuestionGenerator retroQuestionGenerator;
  private final RetroQuestionWriter retroQuestionWriter;
  private final AiLock aiLock;
  private final StoryCardService storyCardService;
  private final RetroReviewGenerator retroReviewGenerator;
  private final WeekService weekService;

  public record QuestionsResult(boolean created, RetrospectiveQuestionsResponse response) {}

  /** 17번. 새로 만들면 created=true(201) */
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  public QuestionsResult createQuestions(
      Long userId, Long retrospectiveId, RetroQuestionMode requestedMode) {
    findMine(userId, retrospectiveId); // 잠금 전에 소유 확인
    return aiLock.run(
        lockKey(retrospectiveId),
        () -> createQuestionsLocked(userId, retrospectiveId, requestedMode));
  }

  private QuestionsResult createQuestionsLocked(
      Long userId, Long retrospectiveId, RetroQuestionMode requestedMode) {
    RetroQuestionMode mode = requestedMode == null ? RetroQuestionMode.AUTO : requestedMode;
    Retrospective retrospective = findMine(userId, retrospectiveId);
    List<RetroAttachment> attachments = readDoneAttachments(userId, retrospectiveId);
    String hash = RetroMaterialHash.of(retrospective, attachments);

    List<QuestionView> existing =
        aiQuestionService.findAll(QuestionOwner.retrospective(retrospectiveId));
    if (!existing.isEmpty() && mode != RetroQuestionMode.REGENERATE) {
      boolean changed = !hash.equals(retrospective.getQuestionInputHash());
      if (changed && mode == RetroQuestionMode.AUTO) {
        throw materialChanged(existing);
      }
      if (changed) {
        retroQuestionWriter.continueWith(retrospectiveId, hash);
      }
      return new QuestionsResult(false, RetrospectiveQuestionsResponse.of(existing));
    }

    if (!retrospective.hasMaterial() && attachments.isEmpty()) {
      return new QuestionsResult(false, RetrospectiveQuestionsResponse.insufficient()); // AI 안 부름
    }
    Part part = userRepository.findById(userId).map(User::getPart).orElse(null);
    Validated generated = retroQuestionGenerator.generate(retrospective, part, attachments);
    if (generated.insufficientMaterial()) {
      return new QuestionsResult(false, RetrospectiveQuestionsResponse.insufficient());
    }
    List<QuestionView> saved =
        retroQuestionWriter.replace(retrospectiveId, generated.questions(), hash);
    return new QuestionsResult(true, RetrospectiveQuestionsResponse.of(saved));
  }

  /**
   * 읽는 중(PENDING)이 끝나길 최대 10초 기다린 뒤(넘으면 409 ATTACHMENT_PENDING), DONE 첨부만 ID별로 읽는다. FAILED·SKIPPED는
   * 뺀다
   */
  private List<RetroAttachment> readDoneAttachments(Long userId, Long retrospectiveId) {
    AttachmentOwner owner = AttachmentOwner.retrospective(retrospectiveId);
    attachmentService.readTexts(owner); // 기다리기만 하고 결과는 쓰지 않는다
    return attachmentService.findAll(owner).stream()
        .filter(view -> view.extractStatus() == ExtractStatus.DONE)
        .map(view -> attachmentService.getOwnedAttachment(userId, view.attachmentId()))
        .map(
            a ->
                new RetroAttachment(
                    a.getId(),
                    a.getType().name(),
                    a.getCategory().name(),
                    a.getName() != null ? a.getName() : a.getUrl(),
                    a.getExtractedText()))
        .toList();
  }

  /** 409 MATERIAL_CHANGED. FE가 확인창에 '답 N개가 사라져요'를 띄우도록 detail에 answeredCount를 넣는다 */
  private static BusinessException materialChanged(List<QuestionView> existing) {
    long answered = existing.stream().filter(q -> q.status() == QuestionStatus.ANSWERED).count();
    return new BusinessException(
        ErrorCode.MATERIAL_CHANGED,
        "자료가 바뀌었어요. 질문을 새로 만들면 지금까지 쓴 답 %d개가 사라져요.".formatted(answered),
        Map.of("answeredCount", answered));
  }

  private Retrospective findMine(Long userId, Long retrospectiveId) {
    return retrospectiveRepository
        .findByIdAndUserId(retrospectiveId, userId)
        .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
  }

  /** 17·19번이 같은 키. AI가 도는 동안 같은 회고에 AI를 또 부르지 못하게 */
  static String lockKey(Long retrospectiveId) {
    return "retrospective:" + retrospectiveId;
  }

  /** 19번: 회고록·카드 초안 (AI 호출, 최대 50초). 17번과 같은 잠금 키 */
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  public RetrospectiveReviewResponse review(Long userId, Long retrospectiveId) {
    findMine(userId, retrospectiveId);
    return aiLock.run(lockKey(retrospectiveId), () -> reviewLocked(userId, retrospectiveId));
  }

  private RetrospectiveReviewResponse reviewLocked(Long userId, Long retrospectiveId) {
    Retrospective retrospective = findMine(userId, retrospectiveId);
    List<RetroAttachment> attachments = readDoneAttachments(userId, retrospectiveId);
    List<QuestionView> answered =
        aiQuestionService.findAll(QuestionOwner.retrospective(retrospectiveId)).stream()
            .filter(q -> q.status() == QuestionStatus.ANSWERED)
            .toList();
    if (!retrospective.hasMaterial() && attachments.isEmpty() && answered.isEmpty()) {
      throw new BusinessException(ErrorCode.EMPTY_MATERIAL);
    }

    Part part = userRepository.findById(userId).map(User::getPart).orElse(null);
    Long weekId = retrospective.getWeekId();
    Integer weekNumber = weekService.findWeekNumbers(List.of(weekId)).get(weekId);
    RetroCardDraft draft =
        retroReviewGenerator.generate(
            retrospective, weekNumber, sessionTitle(weekId, part), part, attachments, answered);
    return new RetrospectiveReviewResponse(storyCardService.saveRetrospectiveDraft(draft));
  }

  /** 19번 카드 제목에 쓰는 그 주차 내 파트의 세션명. 파트를 모르거나 그 주차 일정이 없으면 null. */
  private String sessionTitle(Long weekId, Part part) {
    if (part == null) {
      return null;
    }
    WeekService.WeekSchedule schedule =
        weekService.findSchedules(List.of(weekId), part).get(weekId);
    return schedule == null ? null : schedule.sessionTitle();
  }
}
