package com.sajarok.domain.attachment.service;

import com.sajarok.infra.storage.S3AttachmentStorage;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.exception.SdkException;

@Slf4j
@Component
@RequiredArgsConstructor
public class TxtAttachmentExtractionJob {

  private final S3AttachmentStorage storage;
  private final TxtAttachmentExtractionService extractionService;
  private final AttachmentService attachmentService;

  @Async
  public void run(Long attachmentId, String s3Key) {
    try (var input = storage.open(s3Key)) {
      extractionService.extractAndSave(attachmentId, input);
    } catch (IOException | SdkException e) {
      log.warn("TXT 첨부를 읽지 못했습니다. attachmentId={}", attachmentId, e);
      attachmentService.saveExtractionResult(attachmentId, null);
    }
  }
}
