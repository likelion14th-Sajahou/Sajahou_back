package com.sajarok.domain.attachment.dto;

import com.sajarok.domain.attachment.entity.Attachment;
import com.sajarok.domain.attachment.entity.AttachmentCategory;
import com.sajarok.domain.attachment.entity.AttachmentType;
import com.sajarok.domain.attachment.entity.ExtractStatus;
import com.sajarok.domain.attachment.entity.LinkType;

public record AttachmentLinkResponse(
    Long attachmentId,
    AttachmentType type,
    AttachmentCategory category,
    String url,
    LinkType linkType,
    ExtractStatus extractStatus) {

  public static AttachmentLinkResponse from(Attachment attachment) {
    return new AttachmentLinkResponse(
        attachment.getId(),
        attachment.getType(),
        attachment.getCategory(),
        attachment.getUrl(),
        attachment.getLinkType(),
        attachment.getExtractStatus());
  }
}
