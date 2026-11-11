package com.sajarok.domain.attachment.dto;

import com.sajarok.domain.attachment.entity.Attachment;
import com.sajarok.domain.attachment.entity.AttachmentCategory;
import com.sajarok.domain.attachment.entity.AttachmentType;
import com.sajarok.domain.attachment.entity.ExtractStatus;
import com.sajarok.domain.attachment.entity.LinkType;

public record AttachmentView(
    Long attachmentId,
    AttachmentType type,
    AttachmentCategory category,
    String name,
    String url,
    LinkType linkType,
    String contentType,
    Long sizeBytes,
    ExtractStatus extractStatus) {

  public static AttachmentView from(Attachment attachment) {
    return new AttachmentView(
        attachment.getId(),
        attachment.getType(),
        attachment.getCategory(),
        attachment.getName(),
        attachment.getUrl(),
        attachment.getLinkType(),
        attachment.getContentType(),
        attachment.getSizeBytes(),
        attachment.getExtractStatus());
  }
}
