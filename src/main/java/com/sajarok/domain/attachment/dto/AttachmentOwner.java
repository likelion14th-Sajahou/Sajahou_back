package com.sajarok.domain.attachment.dto;

public record AttachmentOwner(Kind kind, Long id) {

  public enum Kind {
    RETROSPECTIVE,
    PROJECT_RECORD,
    PROJECT,
    JOB_POSTING
  }

  public static AttachmentOwner retrospective(Long id) {
    return new AttachmentOwner(Kind.RETROSPECTIVE, id);
  }

  public static AttachmentOwner projectRecord(Long id) {
    return new AttachmentOwner(Kind.PROJECT_RECORD, id);
  }

  public static AttachmentOwner project(Long id) {
    return new AttachmentOwner(Kind.PROJECT, id);
  }

  public static AttachmentOwner jobPosting(Long id) {
    return new AttachmentOwner(Kind.JOB_POSTING, id);
  }
}
