package com.sajarok.domain.attachment.dto;

import com.sajarok.domain.attachment.entity.Attachment;
import com.sajarok.domain.attachment.entity.AttachmentCategory;
import com.sajarok.domain.attachment.entity.AttachmentType;
import com.sajarok.domain.attachment.entity.ExtractStatus;
import com.sajarok.domain.attachment.entity.LinkType;

public record AttachmentStatusResponse(
    Long attachmentId,
    AttachmentType type,
    AttachmentCategory category,
    LinkType linkType,
    ExtractStatus extractStatus) {

  public static AttachmentStatusResponse from(Attachment attachment) {
    return new AttachmentStatusResponse(
        attachment.getId(),
        attachment.getType(),
        attachment.getCategory(),
        attachment.getLinkType(),
        attachment.getExtractStatus());
  }
}
