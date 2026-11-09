package com.sajarok.domain.user.entity;

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
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 임시 회원 엔티티. 인증 담당자가 올리는 엔티티로 교체한다. */
@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User extends BaseTimeEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, unique = true)
  private String email;

  @Column(nullable = false, unique = true)
  private String providerId;

  @Column(nullable = false, length = 50)
  private String name;

  @Column(nullable = false, length = 20)
  private String studentNo;

  @Column(nullable = false, length = 50)
  private String department;

  @Column(nullable = false)
  private int cohort;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private Part part;

  @Column(nullable = false)
  private LocalDateTime privacyAgreedAt;
}
