package com.sajarok.domain.retrospective.service;

import com.sajarok.domain.question.dto.QuestionView;
import com.sajarok.domain.question.entity.QuestionOwner;
import com.sajarok.domain.question.service.AiQuestionService;
import com.sajarok.domain.retrospective.dto.HomeRetroSummary;
import com.sajarok.domain.retrospective.dto.RetrospectiveDetailResponse;
import com.sajarok.domain.retrospective.dto.RetrospectiveDraftRequest;
import com.sajarok.domain.retrospective.dto.RetrospectiveProgressResponse;
import com.sajarok.domain.retrospective.dto.RetrospectiveRestResponse;
import com.sajarok.domain.retrospective.dto.RetrospectiveStartRequest;
import com.sajarok.domain.retrospective.dto.RetrospectiveStartResponse;
import com.sajarok.domain.retrospective.dto.RetrospectiveState;
import com.sajarok.domain.retrospective.dto.WeekRetroStatus;
import com.sajarok.domain.retrospective.entity.AssignmentMode;
import com.sajarok.domain.retrospective.entity.Retrospective;
import com.sajarok.domain.retrospective.entity.RetrospectiveType;
import com.sajarok.domain.retrospective.repository.RetrospectiveRepository;
import com.sajarok.domain.storycard.dto.StoryCardDetailResponse;
import com.sajarok.domain.storycard.service.StoryCardService;
import com.sajarok.global.exception.BusinessException;
import com.sajarok.global.exception.ErrorCode;
import com.sajarok.global.time.KstTime;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RetrospectiveService {

  private final RetrospectiveRepository retrospectiveRepository;
  private final AiQuestionService aiQuestionService;
  private final StoryCardService storyCardService;

  public HomeRetroSummary homeSummary(Long userId, Long currentWeekId) {
    return new HomeRetroSummary(null, RetrospectiveState.NOT_STARTED, List.of(), true); // TODO fake
  }

  public List<WeekRetroStatus> footprints(Long userId) {
    return List.of();
  }

  /** 12번: 회고 시작. 이미 있으면 그 회고를 그대로 돌려준다 */
  @Transactional
  public StartResult start(Long userId, RetrospectiveStartRequest request) {

    Optional<Retrospective> existing =
        retrospectiveRepository.findByUserIdAndWeekIdAndType(
            userId, request.weekId(), request.type());
    if (existing.isPresent()) {
      return new StartResult(RetrospectiveStartResponse.from(existing.get()), false);
    }

    if (request.type() == RetrospectiveType.ASSIGNMENT) {
      if (request.wantsRest()) {
        throw new BusinessException(ErrorCode.VALIDATION_ERROR, "휴회는 세션 회고만 할 수 있어요.");
      }

      retrospectiveRepository
          .findByUserIdAndWeekIdAndType(userId, request.weekId(), RetrospectiveType.SESSION)
          .filter(session -> session.getAssignmentMode() == AssignmentMode.LATER)
          .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_STATE));
    }

    Retrospective retrospective =
        request.wantsRest()
            ? Retrospective.startRested(userId, request.weekId())
            : Retrospective.start(userId, request.weekId(), request.type());
    return new StartResult(
        RetrospectiveStartResponse.from(retrospectiveRepository.save(retrospective)), true);
  }

  /** 응답과 '새로 만들었는지'를 같이 돌려준다. 컨트롤러가 201/200을 고른다 */
  public record StartResult(RetrospectiveStartResponse response, boolean created) {}

  /** 15번: 휴회 표시·해제 */
  @Transactional
  public RetrospectiveRestResponse changeRested(Long userId, Long retrospectiveId, boolean rested) {
    Retrospective retrospective = findMine(userId, retrospectiveId);
    retrospective.changeRested(rested);
    return RetrospectiveRestResponse.from(retrospective);
  }

  /** 없거나 남의 회고면 NOT_FOUND. 13·14·16·20번에서도 쓴다 */
  private Retrospective findMine(Long userId, Long retrospectiveId) {
    return retrospectiveRepository
        .findByIdAndUserId(retrospectiveId, userId)
        .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
  }

  /** 16번: 처음부터. 2단계 자료·AI 질문·카드 초안·첨부를 지우고 2단계로 돌아간다 */
  @Transactional
  public RetrospectiveProgressResponse reset(Long userId, Long retrospectiveId) {
    Retrospective retrospective = findMine(userId, retrospectiveId);
    retrospective.reset();
    aiQuestionService.deleteAll(QuestionOwner.retrospective(retrospectiveId));
    storyCardService.deleteByRetrospective(retrospectiveId);
    // TODO(첨부 담당): attachmentService.deleteAll(AttachmentOwner.retrospective(retrospectiveId)). S3
    // 파일까지

    return RetrospectiveProgressResponse.from(retrospective);
  }

  /** 13번: 회고 상세 */
  public RetrospectiveDetailResponse getDetail(Long userId, Long retrospectiveId) {
    Retrospective retrospective = findMine(userId, retrospectiveId);
    List<QuestionView> questions =
        aiQuestionService.findAll(QuestionOwner.retrospective(retrospectiveId));
    StoryCardDetailResponse card =
        storyCardService.findDetailByRetrospective(retrospectiveId).orElse(null);
    return RetrospectiveDetailResponse.of(retrospective, questions, card);
  }

  /** 14번: 임시저장. 완료한 회고도 고칠 수 있다 */
  @Transactional
  public RetrospectiveProgressResponse saveDraft(
      Long userId, Long retrospectiveId, RetrospectiveDraftRequest request) {
    Retrospective retrospective = findMine(userId, retrospectiveId);
    // TODO(주차 담당): 진행 중인 회고가 마감 + 7일을 넘겼으면 409 DEADLINE_PASSED (완료한 회고는 기간 상관없음)
    // TODO(회원 담당): codeReview는 FE 파트만. 아니면 400 VALIDATION_ERROR
    retrospective.saveDraft(request, LocalDateTime.now(KstTime.OFFSET));
    // TODO(첨부 담당): request.has("attachmentIds")면 attachmentService.connect(userId,
    // request.getAttachmentIds(), AttachmentOwner.retrospective(retrospectiveId))
    return RetrospectiveProgressResponse.from(retrospective);
  }
}
