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

@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User extends BaseTimeEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false)
  private String email;

  @Column(name = "provider_id", nullable = false)
  private String providerId;

  @Column(nullable = false, length = 50)
  private String name;

  @Column(name = "student_no", nullable = false, length = 20)
  private String studentNo;

  @Column(nullable = false, length = 50)
  private String department;

  @Column(nullable = false)
  private int cohort;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private Part part;

  @Column(name = "privacy_agreed_at", nullable = false)
  private LocalDateTime privacyAgreedAt;
}
