package com.sajarok.domain.storycard.service;

import com.sajarok.domain.competency.service.CompetencyService;
import com.sajarok.domain.project.service.ProjectRecordService;
import com.sajarok.domain.project.service.ProjectService;
import com.sajarok.domain.storycard.dto.StoryCardDetailResponse;
import com.sajarok.domain.storycard.dto.StoryCardListResponse;
import com.sajarok.domain.storycard.dto.StoryCardUpdateRequest;
import com.sajarok.domain.storycard.entity.StoryCard;
import com.sajarok.domain.storycard.entity.StoryCardStatus;
import com.sajarok.domain.storycard.entity.StoryCardType;
import com.sajarok.domain.storycard.repository.StoryCardRepository;
import com.sajarok.domain.week.service.WeekService;
import com.sajarok.global.exception.BusinessException;
import com.sajarok.global.exception.ErrorCode;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StoryCardFacade {

  private final StoryCardRepository storyCardRepository;
  private final ProjectRecordService projectRecordService;
  private final ProjectService projectService;
  private final CompetencyService competencyService;
  private final WeekService weekService;

  /** 24번: 카드 삭제. 프로젝트 카드는 기록째 지운다 */
  @Transactional
  public void delete(Long userId, Long cardId) {
    StoryCard card = findMyCard(userId, cardId);

    if (card.getType() == StoryCardType.PROJECT) {
      projectRecordService.delete(userId, card.getProjectRecordId());
    } else {
      storyCardRepository.delete(card);
    }
  }

  /** 22번: 카드 상세 */
  public StoryCardDetailResponse getDetail(Long userId, Long cardId) {
    return toDetail(findMyCard(userId, cardId));
  }

  /** 23번: 카드 수정. 보낸 칸만 바꾸고, 고친 카드 전체를 22번과 같은 모양으로 돌려준다 */
  @Transactional
  public StoryCardDetailResponse update(Long userId, Long cardId, StoryCardUpdateRequest request) {
    StoryCard card = findMyCard(userId, cardId);

    // 태그 검사(개수·내 파트)를 먼저 해서, 실패하면 아무것도 바뀌지 않게 한다
    if (request.competencyIds() != null) {
      Set<Long> ids =
          competencyService.validate(
              userId, request.competencyIds(), card.getStatus() == StoryCardStatus.PUBLISHED);
      card.changeCompetencies(ids);
    }

    card.editContent(
        request.title(),
        request.situation(),
        request.action(),
        request.reason(),
        request.result(),
        request.learned(),
        request.fullMarkdown());
    return toDetail(card);
  }

  /** 없거나 남의 카드면 NOT_FOUND */
  private StoryCard findMyCard(Long userId, Long cardId) {
    return storyCardRepository
        .findByIdAndUserId(cardId, userId)
        .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
  }

  /** 상세 응답. 프로젝트 카드면 프로젝트 이름과 기록 날짜를, 모든 카드에 역량 이름을 붙인다 */
  private StoryCardDetailResponse toDetail(StoryCard card) {
    String projectName =
        card.getProjectId() == null
            ? null
            : projectService.names(List.of(card.getProjectId())).get(card.getProjectId());
    LocalDate recordDate =
        card.getProjectRecordId() == null
            ? null
            : projectRecordService
                .findDates(List.of(card.getProjectRecordId()))
                .get(card.getProjectRecordId());

    Integer weekNumber =
        card.getWeekId() == null
            ? null
            : weekService.findWeekNumbers(List.of(card.getWeekId())).get(card.getWeekId());

    return StoryCardDetailResponse.of(
        card,
        projectName,
        recordDate,
        competencyService.names(card.getCompetencyIds()),
        weekNumber);
  }

  /** 21번: 카드 목록. 공개 카드만 최신순, 조건은 모두 선택 */
  public StoryCardListResponse getList(
      Long userId, StoryCardType type, Long competencyId, Integer weekNumber, String q) {
    List<StoryCard> published =
        storyCardRepository.findAllByUserIdAndStatusOrderByCreatedAtDescIdDesc(
            userId, StoryCardStatus.PUBLISHED);

    // 주차 번호는 거르기에도 쓰니 먼저 한 번에 가져온다. 프로젝트 카드는 weekId가 없다
    Map<Long, Integer> weekNumbers =
        weekService.findWeekNumbers(
            published.stream()
                .map(StoryCard::getWeekId)
                .filter(Objects::nonNull)
                .distinct()
                .toList());

    List<StoryCard> cards =
        published.stream()
            .filter(card -> type == null || card.getType() == type)
            .filter(card -> competencyId == null || card.getCompetencyIds().contains(competencyId))
            .filter(
                card ->
                    weekNumber == null
                        || (card.getWeekId() != null
                            && weekNumber.equals(weekNumbers.get(card.getWeekId()))))
            .filter(card -> matchesKeyword(card, q))
            .toList();

    // 프로젝트 이름·기록 날짜·역량 이름은 카드마다 묻지 않고 한 번에 가져온다 (N+1 방지)
    Map<Long, String> projectNames =
        projectService.names(
            cards.stream().map(StoryCard::getProjectId).filter(Objects::nonNull).toList());
    Map<Long, LocalDate> recordDates =
        projectRecordService.findDates(
            cards.stream().map(StoryCard::getProjectRecordId).filter(Objects::nonNull).toList());
    Map<Long, String> competencyNames =
        competencyService.names(
            cards.stream().flatMap(card -> card.getCompetencyIds().stream()).distinct().toList());

    List<StoryCardListResponse.CardItem> items =
        cards.stream()
            .map(
                card ->
                    StoryCardListResponse.CardItem.of(
                        card,
                        card.getProjectId() == null ? null : projectNames.get(card.getProjectId()),
                        card.getProjectRecordId() == null
                            ? null
                            : recordDates.get(card.getProjectRecordId()),
                        competencyNames,
                        card.getWeekId() == null ? null : weekNumbers.get(card.getWeekId())))
            .toList();
    return new StoryCardListResponse(items.size(), items);
  }

  /**
   * 21번 검색(q): 제목, 5칸(상황·행동·이유·결과·배운 점), 전문(회고록·기록 전문) 중 한 곳에라도 있으면 남긴다. 영문 대소문자는 가리지 않는다. q가 없거나
   * 공백뿐이면 거르지 않는다.
   */
  private static boolean matchesKeyword(StoryCard card, String q) {
    if (q == null || q.isBlank()) {
      return true;
    }
    String keyword = q.strip().toLowerCase(Locale.ROOT);
    return Stream.of(
            card.getTitle(),
            card.getSituation(),
            card.getAction(),
            card.getReason(),
            card.getResult(),
            card.getLearned(),
            card.getFullMarkdown())
        .anyMatch(text -> text != null && text.toLowerCase(Locale.ROOT).contains(keyword));
  }
}
