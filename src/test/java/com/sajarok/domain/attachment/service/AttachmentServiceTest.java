package com.sajarok.domain.attachment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.sajarok.domain.attachment.entity.Attachment;
import com.sajarok.domain.attachment.entity.AttachmentCategory;
import com.sajarok.domain.attachment.entity.AttachmentType;
import com.sajarok.domain.attachment.entity.ExtractStatus;
import com.sajarok.domain.attachment.repository.AttachmentRepository;
import com.sajarok.infra.storage.S3AttachmentStorage;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@ExtendWith(MockitoExtension.class)
class AttachmentServiceTest {

  @Mock private AttachmentRepository attachmentRepository;
  @Mock private S3AttachmentStorage storage;
  @InjectMocks private AttachmentService attachmentService;

  @Test
  void registerFile_uploadsThenSavesPendingAttachment() {
    var file = textFile();
    when(attachmentRepository.save(any(Attachment.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    Attachment saved = attachmentService.registerFile(2L, file, AttachmentCategory.MEETING_NOTE);

    var key = ArgumentCaptor.forClass(String.class);
    var calls = inOrder(storage, attachmentRepository);
    calls
        .verify(storage)
        .upload(key.capture(), any(InputStream.class), eq(file.getSize()), eq("text/plain"));
    calls.verify(attachmentRepository).save(saved);

    assertThat(saved.getUserId()).isEqualTo(2L);
    assertThat(saved.getType()).isEqualTo(AttachmentType.FILE);
    assertThat(saved.getCategory()).isEqualTo(AttachmentCategory.MEETING_NOTE);
    assertThat(saved.getName()).isEqualTo("meeting.txt");
    assertThat(saved.getS3Key()).isEqualTo(key.getValue());
    assertThat(saved.getExtractStatus()).isEqualTo(ExtractStatus.PENDING);
  }

  @Test
  void registerFile_deletesUploadedObjectWhenSaveFails() {
    var file = textFile();
    var failure = new IllegalStateException("DB 저장 실패");
    when(attachmentRepository.save(any(Attachment.class))).thenThrow(failure);

    assertThatThrownBy(
            () -> attachmentService.registerFile(2L, file, AttachmentCategory.MEETING_NOTE))
        .isSameAs(failure);

    var key = ArgumentCaptor.forClass(String.class);
    var calls = inOrder(storage, attachmentRepository);
    calls
        .verify(storage)
        .upload(key.capture(), any(InputStream.class), eq(file.getSize()), anyString());
    calls.verify(attachmentRepository).save(any(Attachment.class));
    calls.verify(storage).delete(key.getValue());
  }

  private static MockMultipartFile textFile() {
    return new MockMultipartFile(
        "file", "meeting.txt", "text/plain", "회의 메모".getBytes(StandardCharsets.UTF_8));
  }

  @ParameterizedTest
  @ValueSource(strings = {"capture.png", "capture.jpg"})
  void registerFile_savesImagesAsSkipped(String filename) {
    var file = new MockMultipartFile("file", filename, "application/octet-stream", new byte[] {1});
    when(attachmentRepository.save(any(Attachment.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    Attachment saved = attachmentService.registerFile(2L, file, AttachmentCategory.DESIGN);

    assertThat(saved.getExtractStatus()).isEqualTo(ExtractStatus.SKIPPED);
  }

  @Test
  void delete_callsS3OnlyAfterCommitCallback() {
    Long userId = 2L;
    Long attachmentId = 501L;
    String s3Key = "attachments/delete-test";
    Attachment attachment = mock(Attachment.class);

    when(attachment.getS3Key()).thenReturn(s3Key);
    when(attachmentRepository.findByIdAndUserId(attachmentId, userId))
        .thenReturn(Optional.of(attachment));

    TransactionSynchronizationManager.initSynchronization();
    try {
      attachmentService.delete(userId, attachmentId);

      verify(attachmentRepository).deleteAll(List.of(attachment));
      verifyNoInteractions(storage);

      var callbacks = TransactionSynchronizationManager.getSynchronizations();
      assertThat(callbacks).hasSize(1);
      callbacks.forEach(TransactionSynchronization::afterCommit);

      verify(storage).delete(s3Key);
    } finally {
      TransactionSynchronizationManager.clearSynchronization();
    }
  }
}
