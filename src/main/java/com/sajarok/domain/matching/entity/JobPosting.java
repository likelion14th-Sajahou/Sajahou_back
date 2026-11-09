package com.sajarok.domain.matching.entity;

import com.sajarok.global.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.List;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** 채용 공고 = 자소서 매칭 한 건. */
@Entity
@Table(name = "job_posting")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class JobPosting extends BaseTimeEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  /** 회원 ID. 팀 인터페이스 규칙에 따라 User 엔티티와 연관관계를 맺지 않고 ID로만 참조한다. */
  @Column(name = "user_id", nullable = false)
  private Long userId;

  @Column(length = 100)
  private String company;

  @Column(length = 100)
  private String department;

  @Column(columnDefinition = "TEXT")
  private String postingText;

  @Column(columnDefinition = "TEXT")
  private String jd;

  @Column(columnDefinition = "TEXT")
  private String preferredQualification;

  @Column(columnDefinition = "TEXT")
  private String companyInfo;

  /** 공고가 원하는 역량. AI로 찾기 전에는 NULL. */
  @JdbcTypeCode(SqlTypes.JSON)
  private List<PostingCompetency> competencies;

  @Builder
  private JobPosting(
      Long userId,
      String company,
      String department,
      String postingText,
      String jd,
      String preferredQualification,
      String companyInfo) {
    this.userId = userId;
    this.company = company;
    this.department = department;
    this.postingText = postingText;
    this.jd = jd;
    this.preferredQualification = preferredQualification;
    this.companyInfo = companyInfo;
  }
}
