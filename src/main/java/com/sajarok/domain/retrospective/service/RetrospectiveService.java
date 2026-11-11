package com.sajarok.domain.retrospective.service;

import com.sajarok.domain.retrospective.dto.HomeRetroSummary;
import com.sajarok.domain.retrospective.dto.RetrospectiveRestResponse;
import com.sajarok.domain.retrospective.dto.RetrospectiveStartRequest;
import com.sajarok.domain.retrospective.dto.RetrospectiveStartResponse;
import com.sajarok.domain.retrospective.dto.RetrospectiveState;
import com.sajarok.domain.retrospective.dto.WeekRetroStatus;
import com.sajarok.domain.retrospective.entity.AssignmentMode;
import com.sajarok.domain.retrospective.entity.Retrospective;
import com.sajarok.domain.retrospective.entity.RetrospectiveType;
import com.sajarok.domain.retrospective.repository.RetrospectiveRepository;
import com.sajarok.global.exception.BusinessException;
import com.sajarok.global.exception.ErrorCode;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RetrospectiveService {

  private final RetrospectiveRepository retrospectiveRepository; // 필드 추가

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
}
