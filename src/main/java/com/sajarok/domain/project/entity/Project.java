package com.sajarok.domain.project.entity;

import com.sajarok.domain.user.entity.User;
import com.sajarok.global.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
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

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "user_id", nullable = false)
  private User user;

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
  private Project(User user, String name, String subtitle, List<ProjectRole> roles) {
    this.user = user;
    this.name = name;
    this.subtitle = subtitle;
    this.roles = roles == null ? new ArrayList<>() : new ArrayList<>(roles);
    this.status = ProjectStatus.IN_PROGRESS;
  }
}
