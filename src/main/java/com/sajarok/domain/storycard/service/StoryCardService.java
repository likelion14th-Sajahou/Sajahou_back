package com.sajarok.domain.storycard.service;

import com.sajarok.domain.storycard.dto.CardSnapshot;
import com.sajarok.domain.storycard.dto.RecentCard;
import com.sajarok.domain.storycard.dto.RecordCardInfo;
import com.sajarok.domain.storycard.dto.RecordCardSource;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StoryCardService {

  @Transactional
  public Long createFromRecord(RecordCardSource src, Set<Long> competencyIds) {
    return 0L;
  }

  @Transactional
  public void syncFromRecord(RecordCardSource src, Set<Long> competencyIds) {}

  @Transactional
  public void deleteByRecord(Long recordId) {}

  public Map<Long, RecordCardInfo> findByRecordIds(Collection<Long> recordIds) {
    return Map.of();
  }

  public List<CardSnapshot> findPublished(Long userId) {
    return List.of();
  }

  public Map<Long, Long> countByCompetency(Long userId) {
    return Map.of();
  }

  public List<RecentCard> findRecentRetrospectiveCards(Long userId) {
    return List.of();
  }
}
