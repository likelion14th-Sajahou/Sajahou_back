package com.sajarok.domain.attachment.dto;

import com.sajarok.domain.attachment.entity.Attachment;
import com.sajarok.domain.attachment.entity.AttachmentCategory;
import com.sajarok.domain.attachment.entity.AttachmentType;
import com.sajarok.domain.attachment.entity.ExtractStatus;

public record AttachmentFileResponse(
    Long attachmentId,
    AttachmentType type,
    AttachmentCategory category,
    String name,
    String contentType,
    Long sizeBytes,
    ExtractStatus extractStatus) {

  public static AttachmentFileResponse from(Attachment attachment) {
    return new AttachmentFileResponse(
        attachment.getId(),
        attachment.getType(),
        attachment.getCategory(),
        attachment.getName(),
        attachment.getContentType(),
        attachment.getSizeBytes(),
        attachment.getExtractStatus());
  }
}
