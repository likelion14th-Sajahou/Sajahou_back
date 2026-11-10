package com.sajarok.storycard.domain;

import com.sajarok.global.entity.BaseTimeEntity;
import com.sajarok.global.exception.BusinessException;
import com.sajarok.global.exception.ErrorCode;
import jakarta.persistence.*;
import java.util.HashSet;
import java.util.Set;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "story_card")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StoryCard extends BaseTimeEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "user_id", nullable = false)
  private Long userId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private StoryCardType type;

  @Column(name = "retrospective_id")
  private Long retrospectiveId;

  @Column(name = "project_id")
  private Long projectId;

  @Column(name = "project_record_id")
  private Long projectRecordId;

  @Column(name = "week_id")
  private Long weekId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private StoryCardStatus status;

  @Column(length = 200)
  private String title;

  @Column(columnDefinition = "TEXT")
  private String situation;

  @Column(columnDefinition = "TEXT")
  private String action;

  @Column(columnDefinition = "TEXT")
  private String reason;

  @Column(columnDefinition = "TEXT")
  private String result;

  @Column(columnDefinition = "TEXT")
  private String learned;

  @Column(columnDefinition = "MEDIUMTEXT")
  private String fullMarkdown;

  @Column(columnDefinition = "JSON")
  private String evidence;

  @ElementCollection
  @CollectionTable(name = "card_competency", joinColumns = @JoinColumn(name = "card_id"))
  @Column(name = "competency_id", nullable = false)
  private Set<Long> competencyIds = new HashSet<>();

  public void replaceCompetencies(Set<Long> ids, boolean required) {
    if (ids.size() > 3 || (required && ids.isEmpty())) {
      throw new BusinessException(ErrorCode.COMPETENCY_LIMIT);
    }

    competencyIds.clear();
    competencyIds.addAll(ids);
  }

  public void publish() {
    if (competencyIds.isEmpty() || competencyIds.size() > 3) {
      throw new BusinessException(ErrorCode.COMPETENCY_LIMIT);
    }
    this.status = StoryCardStatus.PUBLISHED;
  }
}
