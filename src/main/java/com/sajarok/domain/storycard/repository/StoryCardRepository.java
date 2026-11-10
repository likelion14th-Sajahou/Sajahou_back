package com.sajarok.domain.storycard.repository;

import com.sajarok.domain.storycard.entity.StoryCard;
import com.sajarok.domain.storycard.entity.StoryCardStatus;
import com.sajarok.domain.storycard.entity.StoryCardType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StoryCardRepository extends JpaRepository<StoryCard, Long> {

  @EntityGraph(attributePaths = "competencyIds")
  Optional<StoryCard> findByIdAndUserId(Long id, Long userId);

  Optional<StoryCard> findByRetrospectiveId(Long retrospectiveId);

  Optional<StoryCard> findByProjectRecordId(Long projectRecordId);

  boolean existsByProjectRecordId(Long projectRecordId);

  @EntityGraph(attributePaths = "competencyIds")
  List<StoryCard> findAllByProjectRecordIdIn(Collection<Long> projectRecordIds);

  @EntityGraph(attributePaths = "competencyIds")
  List<StoryCard> findAllByUserIdAndStatus(Long userId, StoryCardStatus status);

  List<StoryCard> findTop3ByUserIdAndStatusAndTypeNotOrderByCreatedAtDesc(
      Long userId, StoryCardStatus status, StoryCardType type);

  @Query(
      """
    select new com.sajarok.domain.storycard.repository.CompetencyCount(c, count(s))
    from StoryCard s join s.competencyIds c
    where s.userId = :userId and s.status = :status
    group by c
    """)
  List<CompetencyCount> countByCompetency(
      @Param("userId") Long userId, @Param("status") StoryCardStatus status);
}
