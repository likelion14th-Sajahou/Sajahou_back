package com.sajarok.domain.attachment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sajarok.domain.attachment.dto.AttachmentOwner;
import com.sajarok.domain.attachment.entity.Attachment;
import com.sajarok.domain.attachment.entity.ExtractStatus;
import com.sajarok.domain.attachment.repository.AttachmentRepository;
import com.sajarok.global.exception.BusinessException;
import com.sajarok.global.exception.ErrorCode;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AttachmentReadTextsTest {

  private static final AttachmentOwner OWNER = AttachmentOwner.projectRecord(77L);
  private static final List<Long> OWNER_IDS = List.of(77L);

  @Mock private AttachmentRepository attachmentRepository;
  @InjectMocks private AttachmentService attachmentService;

  @Test
  void readTexts_returnsOnlyNonBlankDoneTexts() {
    List<Attachment> attachments =
        List.of(
            done("회의록 본문"),
            withStatus(ExtractStatus.FAILED),
            done("   "),
            withStatus(ExtractStatus.SKIPPED),
            done("발표 자료 본문"));

    when(attachmentRepository.findAllByProjectRecordIdInOrderByIdAsc(OWNER_IDS))
        .thenReturn(attachments);

    assertThat(attachmentService.readTexts(OWNER)).containsExactly("회의록 본문", "발표 자료 본문");
  }

  @Test
  void readTexts_checksAgainWhenAnAttachmentIsPending() {
    List<Attachment> pending = List.of(withStatus(ExtractStatus.PENDING));
    List<Attachment> completed = List.of(done("추출 완료된 본문"));

    when(attachmentRepository.findAllByProjectRecordIdInOrderByIdAsc(OWNER_IDS))
        .thenReturn(pending)
        .thenReturn(completed);

    assertThat(attachmentService.readTexts(OWNER)).containsExactly("추출 완료된 본문");
    verify(attachmentRepository, times(2)).findAllByProjectRecordIdInOrderByIdAsc(OWNER_IDS);
  }

  @Test
  void readTexts_throwsAttachmentPendingWhenExtractionTimesOut() {
    List<Attachment> pending = List.of(withStatus(ExtractStatus.PENDING));

    when(attachmentRepository.findAllByProjectRecordIdInOrderByIdAsc(OWNER_IDS))
        .thenReturn(pending);

    long startedAt = System.nanoTime();

    assertThatThrownBy(() -> attachmentService.readTexts(OWNER))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.ATTACHMENT_PENDING);

    assertThat(Duration.ofNanos(System.nanoTime() - startedAt))
        .isGreaterThanOrEqualTo(Duration.ofSeconds(10));
  }

  private static Attachment done(String text) {
    Attachment attachment = withStatus(ExtractStatus.DONE);
    when(attachment.getExtractedText()).thenReturn(text);
    return attachment;
  }

  private static Attachment withStatus(ExtractStatus status) {
    Attachment attachment = mock(Attachment.class);
    when(attachment.getExtractStatus()).thenReturn(status);
    return attachment;
  }
}
