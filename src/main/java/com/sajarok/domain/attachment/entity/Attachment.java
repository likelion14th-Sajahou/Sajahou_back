package com.sajarok.domain.attachment.entity;

import com.sajarok.domain.attachment.dto.AttachmentOwner;
import com.sajarok.global.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "attachment")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Attachment extends BaseTimeEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "user_id", nullable = false)
  private Long userId;

  @Column(name = "retrospective_id")
  private Long retrospectiveId;

  @Column(name = "project_record_id")
  private Long projectRecordId;

  @Column(name = "project_id")
  private Long projectId;

  @Column(name = "job_posting_id")
  private Long jobPostingId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private AttachmentType type;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 30, columnDefinition = "VARCHAR(30)")
  private AttachmentCategory category;

  @Column(length = 255)
  private String name;

  @Column(name = "s3_key", length = 500)
  private String s3Key;

  @Column(length = 2048)
  private String url;

  @Enumerated(EnumType.STRING)
  @Column(name = "link_type", length = 30, columnDefinition = "VARCHAR(30)")
  private LinkType linkType;

  @Column(name = "content_type", length = 100)
  private String contentType;

  @Column(name = "size_bytes")
  private Long sizeBytes;

  @Column(name = "extracted_text", columnDefinition = "LONGTEXT")
  private String extractedText;

  @Enumerated(EnumType.STRING)
  @Column(name = "extract_status", nullable = false)
  private ExtractStatus extractStatus;

  public static Attachment createLink(
      Long userId, AttachmentCategory category, String url, LinkType linkType) {
    Attachment attachment = new Attachment();
    attachment.userId = userId;
    attachment.type = AttachmentType.LINK;
    attachment.category = category;
    attachment.url = url;
    attachment.linkType = linkType;
    attachment.extractStatus =
        linkType == LinkType.GITHUB ? ExtractStatus.PENDING : ExtractStatus.SKIPPED;
    return attachment;
  }

  public boolean canConnectTo(AttachmentOwner owner) {
    if (retrospectiveId == null
        && projectRecordId == null
        && projectId == null
        && jobPostingId == null) {
      return true;
    }

    return switch (owner.kind()) {
      case RETROSPECTIVE -> owner.id().equals(retrospectiveId);
      case PROJECT_RECORD -> owner.id().equals(projectRecordId);
      case PROJECT -> owner.id().equals(projectId);
      case JOB_POSTING -> owner.id().equals(jobPostingId);
    };
  }

  public void connectTo(AttachmentOwner owner) {
    switch (owner.kind()) {
      case RETROSPECTIVE -> retrospectiveId = owner.id();
      case PROJECT_RECORD -> projectRecordId = owner.id();
      case PROJECT -> projectId = owner.id();
      case JOB_POSTING -> jobPostingId = owner.id();
    }
  }

  public void disconnectFrom(AttachmentOwner owner) {
    switch (owner.kind()) {
      case RETROSPECTIVE -> {
        if (owner.id().equals(retrospectiveId)) {
          retrospectiveId = null;
        }
      }
      case PROJECT_RECORD -> {
        if (owner.id().equals(projectRecordId)) {
          projectRecordId = null;
        }
      }
      case PROJECT -> {
        if (owner.id().equals(projectId)) {
          projectId = null;
        }
      }
      case JOB_POSTING -> {
        if (owner.id().equals(jobPostingId)) {
          jobPostingId = null;
        }
      }
    }
  }

  public static Attachment createFile(
      Long userId,
      AttachmentCategory category,
      String name,
      String s3Key,
      String contentType,
      long sizeBytes,
      ExtractStatus initialStatus) {
    Attachment attachment = new Attachment();
    attachment.userId = userId;
    attachment.type = AttachmentType.FILE;
    attachment.category = category;
    attachment.name = name;
    attachment.s3Key = s3Key;
    attachment.contentType = contentType;
    attachment.sizeBytes = sizeBytes;
    attachment.extractStatus = initialStatus;
    return attachment;
  }
}
