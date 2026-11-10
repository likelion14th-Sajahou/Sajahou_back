package com.sajarok.domain.project.entity;

import com.sajarok.global.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "project")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Project extends BaseTimeEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  /** 회원 ID. 팀 인터페이스 규칙에 따라 User 엔티티와 연관관계를 맺지 않고 ID로만 참조한다. */
  @Column(name = "user_id", nullable = false)
  private Long userId;

  @Column(nullable = false, length = 100)
  private String name;

  @Column(length = 100)
  private String subtitle;

  /** 내 역할 배열. DB에는 JSON 배열(["PM", "BACKEND"])로 저장된다. */
  @JdbcTypeCode(SqlTypes.JSON)
  @Column(nullable = false)
  private List<ProjectRole> roles = new ArrayList<>();

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private ProjectStatus status;

  @Column(columnDefinition = "MEDIUMTEXT")
  private String portfolioMarkdown;

  private LocalDateTime portfolioSavedAt;

  @Builder
  private Project(Long userId, String name, String subtitle, List<ProjectRole> roles) {
    this.userId = userId;
    this.name = name;
    this.subtitle = subtitle;
    this.roles = roles == null ? new ArrayList<>() : new ArrayList<>(roles);
    this.status = ProjectStatus.IN_PROGRESS;
  }

  /**
   * 34번: 포트폴리오를 저장(덮어쓰기)하고 완료(DONE)로 바꾼다.
   *
   * @return 이번에 완료로 바뀌었으면 true(처음 저장), 이미 완료였으면 false(다시 저장)
   */
  public boolean completeWithPortfolio(String markdown, LocalDateTime savedAt) {
    boolean firstCompletion = status == ProjectStatus.IN_PROGRESS;
    this.portfolioMarkdown = markdown;
    this.portfolioSavedAt = savedAt;
    this.status = ProjectStatus.DONE;
    return firstCompletion;
  }
}
