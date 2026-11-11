package com.sajarok.domain.storycard.service;

import com.sajarok.domain.project.service.ProjectRecordService;
import com.sajarok.domain.project.service.ProjectService;
import com.sajarok.domain.storycard.dto.StoryCardDetailResponse;
import com.sajarok.domain.storycard.dto.StoryCardListResponse;
import com.sajarok.domain.storycard.dto.StoryCardUpdateRequest;
import com.sajarok.domain.storycard.entity.StoryCard;
import com.sajarok.domain.storycard.entity.StoryCardStatus;
import com.sajarok.domain.storycard.entity.StoryCardType;
import com.sajarok.domain.storycard.repository.StoryCardRepository;
import com.sajarok.global.exception.BusinessException;
import com.sajarok.global.exception.ErrorCode;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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

    card.editContent(
        request.title(),
        request.situation(),
        request.action(),
        request.reason(),
        request.result(),
        request.learned(),
        request.fullMarkdown());
    if (request.competencyIds() != null) {
      card.changeCompetencies(request.competencyIds());
    }
    return toDetail(card);
  }

  /** 없거나 남의 카드면 NOT_FOUND */
  private StoryCard findMyCard(Long userId, Long cardId) {
    return storyCardRepository
        .findByIdAndUserId(cardId, userId)
        .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
  }

  /** 상세 응답. 프로젝트 카드면 프로젝트 이름과 기록 날짜를 붙인다 */
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

    return StoryCardDetailResponse.of(card, projectName, recordDate);
  }

  /** 21번: 카드 목록. 공개 카드만 최신순, 조건은 모두 선택 */
  public StoryCardListResponse getList(
      Long userId, StoryCardType type, Long competencyId, Integer weekNumber, String q) {
    List<StoryCard> cards =
        storyCardRepository
            .findAllByUserIdAndStatusOrderByCreatedAtDescIdDesc(userId, StoryCardStatus.PUBLISHED)
            .stream()
            .filter(card -> type == null || card.getType() == type)
            .filter(card -> competencyId == null || card.getCompetencyIds().contains(competencyId))
            // TODO(주차 담당): weekNumber를 weekId로 바꿔서 거른다. 지금은 받기만 한다
            .filter(
                card ->
                    q == null
                        || q.isBlank()
                        || (card.getTitle() != null && card.getTitle().contains(q.strip())))
            .toList();

    // 프로젝트 이름·기록 날짜는 카드마다 묻지 않고 한 번에 가져온다 (N+1 방지)
    Map<Long, String> projectNames =
        projectService.names(
            cards.stream().map(StoryCard::getProjectId).filter(Objects::nonNull).toList());
    Map<Long, LocalDate> recordDates =
        projectRecordService.findDates(
            cards.stream().map(StoryCard::getProjectRecordId).filter(Objects::nonNull).toList());

    List<StoryCardListResponse.CardItem> items =
        cards.stream()
            .map(
                card ->
                    StoryCardListResponse.CardItem.of(
                        card,
                        card.getProjectId() == null ? null : projectNames.get(card.getProjectId()),
                        card.getProjectRecordId() == null
                            ? null
                            : recordDates.get(card.getProjectRecordId())))
            .toList();
    return new StoryCardListResponse(items.size(), items);
  }
}
