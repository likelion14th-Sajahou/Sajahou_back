package com.sajarok.domain.competency.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "competency")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Competency {

  public enum Scope {
    COMMON,
    PLAN_DESIGN,
    FRONTEND,
    BACKEND
  }

  @Id private Long id;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private Scope part;

  @Column(nullable = false, length = 50)
  private String name;

  @Column(nullable = false, length = 200)
  private String description;

  @Column(name = "sort_order", nullable = false)
  private int sortOrder;
}
