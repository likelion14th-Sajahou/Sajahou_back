package com.sajarok.domain.attachment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sajarok.domain.attachment.dto.AttachmentOwner;
import com.sajarok.domain.attachment.entity.Attachment;
import com.sajarok.domain.attachment.entity.AttachmentCategory;
import com.sajarok.domain.attachment.entity.LinkType;
import com.sajarok.domain.attachment.repository.AttachmentRepository;
import com.sajarok.infra.storage.S3AttachmentStorage;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class AttachmentCleanupTest {

  private static final Long ATTACHMENT_ID = 7L;
  private static final LocalDateTime CUTOFF = LocalDateTime.of(2026, 11, 12, 10, 0);

  @Mock private AttachmentRepository attachmentRepository;
  @Mock private S3AttachmentStorage storage;

  @InjectMocks private AttachmentService attachmentService;

  @Test
  void deletesExpiredUnlinkedAttachment() {
    Attachment attachment = linkAttachment(CUTOFF.minusHours(1));
    when(attachmentRepository.findByIdForUpdate(ATTACHMENT_ID)).thenReturn(Optional.of(attachment));

    assertThat(attachmentService.deleteExpiredUnlinked(ATTACHMENT_ID, CUTOFF)).isTrue();

    verify(attachmentRepository).deleteAll(List.of(attachment));
  }

  @Test
  void keepsRecentlyUpdatedAttachment() {
    Attachment attachment = linkAttachment(CUTOFF.plusMinutes(1));
    when(attachmentRepository.findByIdForUpdate(ATTACHMENT_ID)).thenReturn(Optional.of(attachment));

    assertThat(attachmentService.deleteExpiredUnlinked(ATTACHMENT_ID, CUTOFF)).isFalse();

    verify(attachmentRepository, never()).deleteAll(List.of(attachment));
  }

  @Test
  void keepsAttachmentLinkedAfterCandidateLookup() {
    Attachment attachment = linkAttachment(CUTOFF.minusHours(1));
    attachment.connectTo(AttachmentOwner.projectRecord(77L));
    when(attachmentRepository.findByIdForUpdate(ATTACHMENT_ID)).thenReturn(Optional.of(attachment));

    assertThat(attachmentService.deleteExpiredUnlinked(ATTACHMENT_ID, CUTOFF)).isFalse();

    verify(attachmentRepository, never()).deleteAll(List.of(attachment));
  }

  private static Attachment linkAttachment(LocalDateTime updatedAt) {
    Attachment attachment =
        Attachment.createLink(1L, AttachmentCategory.OTHER, "https://example.com", LinkType.OTHER);
    ReflectionTestUtils.setField(attachment, "updatedAt", updatedAt);
    return attachment;
  }
}
