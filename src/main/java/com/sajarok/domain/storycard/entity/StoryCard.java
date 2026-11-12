package com.sajarok.domain.storycard.entity;

import com.sajarok.domain.storycard.dto.RecordCardSource;
import com.sajarok.domain.storycard.dto.RetroCardDraft;
import com.sajarok.global.entity.BaseTimeEntity;
import com.sajarok.global.exception.BusinessException;
import com.sajarok.global.exception.ErrorCode;
import jakarta.persistence.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
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

  public static StoryCard fromRecord(RecordCardSource src, Set<Long> competencyIds) {
    StoryCard c = new StoryCard();
    c.userId = src.userId();
    c.type = StoryCardType.PROJECT;
    c.projectId = src.projectId();
    c.projectRecordId = src.recordId();
    c.status = StoryCardStatus.PUBLISHED;
    c.title = titleOf(src);
    c.applyRecord(src);
    c.replaceCompetencies(competencyIds, true);
    return c;
  }

  public void syncFromRecord(RecordCardSource src, Set<Long> competencyIds) {
    applyRecord(src);
    if (competencyIds != null) {
      replaceCompetencies(competencyIds, true);
    }
  }

  private void applyRecord(RecordCardSource src) {
    this.situation = blankToNull(src.concern());
    this.action = blankToNull(src.did());
    this.reason = blankToNull(src.decision());
    this.result = blankToNull(src.result());
    this.fullMarkdown = markdownOf(this.title, src);
  }

  private static String titleOf(RecordCardSource src) {
    String title = firstLine(src.did());
    if (title == null) {
      title = firstLine(src.memo());
    }
    if (title == null) {
      title = src.date() + " 기록";
    }
    return title.length() <= 200 ? title : title.substring(0, 200);
  }

  private static String firstLine(String text) {
    if (text == null) {
      return null;
    }
    for (String line : text.split("\\R")) {
      if (!line.isBlank()) {
        return line.strip();
      }
    }
    return null;
  }

  private static String blankToNull(String s) {
    return (s == null || s.isBlank()) ? null : s;
  }

  private static String markdownOf(String title, RecordCardSource src) {
    StringBuilder md = new StringBuilder("# ").append(title).append("\n\n");
    md.append(src.date()).append(" 기록\n");
    section(md, "한 일", src.did());
    section(md, "고민·해결", src.concern());
    section(md, "선택과 이유", src.decision());
    section(md, "결과·반응", src.result());
    section(md, "다음", src.nextStep());
    section(md, "메모", src.memo());
    return md.toString();
  }

  private static void section(StringBuilder md, String heading, String body) {
    if (blankToNull(body) == null) {
      return;
    }
    md.append("\n## ").append(heading).append("\n\n").append(body.strip()).append("\n");
  }

  public List<String> blanks() {
    List<String> blanks = new ArrayList<>();
    if (isBlank(situation)) blanks.add("situation");
    if (isBlank(action)) blanks.add("action");
    if (isBlank(reason)) blanks.add("reason");
    if (isBlank(result)) blanks.add("result");
    if (isBlank(learned)) blanks.add("learned");

    return blanks;
  }

  private static boolean isBlank(String s) {
    return s == null || s.isBlank();
  }

  /** 23번 수정: 보낸 칸만 바꾼다. null이면 그대로, 빈 문자열이면 비운다 */
  public void editContent(
      String title,
      String situation,
      String action,
      String reason,
      String result,
      String learned,
      String fullMarkdown) {
    if (title != null) this.title = blankToNull(title);
    if (situation != null) this.situation = blankToNull(situation);
    if (action != null) this.action = blankToNull(action);
    if (reason != null) this.reason = blankToNull(reason);
    if (result != null) this.result = blankToNull(result);
    if (learned != null) this.learned = blankToNull(learned);
    if (fullMarkdown != null) this.fullMarkdown = blankToNull(fullMarkdown);
  }

  /** 23번 태그 바꾸기: 초안은 0~3개, 공개된 카드는 1~3개 */
  public void changeCompetencies(Set<Long> ids) {
    replaceCompetencies(ids, status == StoryCardStatus.PUBLISHED);
  }

  /** 19번: 회고로 카드 초안(DRAFT)을 만든다 */
  public static StoryCard retrospectiveDraft(RetroCardDraft d) {
    StoryCard c = new StoryCard();
    c.userId = d.userId();
    c.type = d.type();
    c.retrospectiveId = d.retrospectiveId();
    c.weekId = d.weekId();
    c.status = StoryCardStatus.DRAFT;
    c.applyRetrospectiveDraft(d);
    return c;
  }

  /** 19번: 다시 만들면 내용만 덮어쓴다. 고른 역량 태그와 상태(공개 여부)는 그대로 둔다 (R-20) */
  public void applyRetrospectiveDraft(RetroCardDraft d) {
    this.title = d.title();
    this.situation = d.situation();
    this.action = d.action();
    this.reason = d.reason();
    this.result = d.result();
    this.learned = d.learned();
    this.fullMarkdown = d.fullMarkdown();
    this.evidence = d.evidence();
  }
}
