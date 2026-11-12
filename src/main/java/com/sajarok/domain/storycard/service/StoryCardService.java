package com.sajarok.domain.storycard.service;

import com.sajarok.domain.storycard.dto.CardSnapshot;
import com.sajarok.domain.storycard.dto.RecentCard;
import com.sajarok.domain.storycard.dto.RecordCardInfo;
import com.sajarok.domain.storycard.dto.RecordCardSource;
import com.sajarok.domain.storycard.dto.StoryCardDetailResponse;
import com.sajarok.domain.storycard.entity.StoryCard;
import com.sajarok.domain.storycard.entity.StoryCardStatus;
import com.sajarok.domain.storycard.entity.StoryCardType;
import com.sajarok.domain.storycard.repository.CompetencyCount;
import com.sajarok.domain.storycard.repository.StoryCardRepository;
import com.sajarok.global.exception.BusinessException;
import com.sajarok.global.exception.ErrorCode;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StoryCardService {

  private final StoryCardRepository storyCardRepository;

  @Transactional
  public Long createFromRecord(RecordCardSource src, Set<Long> competencyIds) {

    if (storyCardRepository.existsByProjectRecordId(src.recordId())) {
      throw new BusinessException(ErrorCode.INVALID_STATE);
    }

    StoryCard card = StoryCard.fromRecord(src, competencyIds);

    return storyCardRepository.save(card).getId();
  }

  @Transactional
  public void syncFromRecord(RecordCardSource src, Set<Long> competencyIds) {
    StoryCard card =
        storyCardRepository
            .findByProjectRecordId(src.recordId())
            .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
    card.syncFromRecord(src, competencyIds);
  }

  @Transactional
  public void deleteByRecord(Long recordId) {
    storyCardRepository.findByProjectRecordId(recordId).ifPresent(storyCardRepository::delete);
  }

  /** 16번 처음부터: 회고의 카드 초안을 지운다. 없으면 아무것도 하지 않는다 */
  @Transactional
  public void deleteByRetrospective(Long retrospectiveId) {
    storyCardRepository
        .findByRetrospectiveId(retrospectiveId)
        .ifPresent(storyCardRepository::delete);
  }

  public Map<Long, RecordCardInfo> findByRecordIds(Collection<Long> recordIds) {
    if (recordIds.isEmpty()) {
      return Map.of();
    }
    return storyCardRepository.findAllByProjectRecordIdIn(recordIds).stream()
        .map(
            card ->
                new RecordCardInfo(
                    card.getProjectRecordId(), card.getId(), Set.copyOf(card.getCompetencyIds())))
        .collect(Collectors.toMap(RecordCardInfo::recordId, info -> info));
  }

  // StoryCardService
  public List<CardSnapshot> findPublished(Long userId) {
    return storyCardRepository.findAllByUserIdAndStatus(userId, StoryCardStatus.PUBLISHED).stream()
        .map(CardSnapshot::from)
        .toList();
  }

  public Map<Long, Long> countByCompetency(Long userId) {
    return storyCardRepository.countByCompetency(userId, StoryCardStatus.PUBLISHED).stream()
        .collect(Collectors.toMap(CompetencyCount::competencyId, CompetencyCount::cardCount));
  }

  public List<RecentCard> findRecentRetrospectiveCards(Long userId) {
    return storyCardRepository
        .findTop3ByUserIdAndStatusAndTypeNotOrderByCreatedAtDesc(
            userId, StoryCardStatus.PUBLISHED, StoryCardType.PROJECT)
        .stream()
        .map(RecentCard::from)
        .toList();
  }

  /** 13번 회고 상세용: 회고의 카드를 22번과 같은 모양으로. 4단계 전이면 비어 있다 */
  public Optional<StoryCardDetailResponse> findDetailByRetrospective(Long retrospectiveId) {
    return storyCardRepository
        .findByRetrospectiveId(retrospectiveId)
        .map(card -> StoryCardDetailResponse.of(card, null, null));
  }

  /** 20번 회고 저장: 회고의 카드 초안을 공개한다. 초안이 없으면 409(19번을 먼저), 태그가 1~3개가 아니면 400 */
  @Transactional
  public Long publishRetrospectiveCard(Long retrospectiveId) {
    StoryCard card =
        storyCardRepository
            .findByRetrospectiveId(retrospectiveId)
            .orElseThrow(
                () -> new BusinessException(ErrorCode.INVALID_STATE, "회고록·카드 초안을 먼저 만들어 주세요."));
    card.publish();
    return card.getId();
  }
}
