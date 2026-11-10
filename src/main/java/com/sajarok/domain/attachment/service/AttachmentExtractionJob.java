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
public class AttachmentExtractionJob {

  private final S3AttachmentStorage storage;
  private final TxtAttachmentExtractionService txtExtractionService;
  private final PdfAttachmentExtractor pdfExtractor;
  private final AttachmentService attachmentService;

  @Async
  public void run(Long attachmentId, String s3Key, boolean pdf) {
    try (var input = storage.open(s3Key)) {
      if (pdf) {
        String text = pdfExtractor.extract(input);
        attachmentService.saveExtractionResult(attachmentId, text);
      } else {
        txtExtractionService.extractAndSave(attachmentId, input);
      }
    } catch (IOException | SdkException e) {
      log.warn("첨부 본문 읽기 실패 attachmentId={}", attachmentId, e);
      attachmentService.saveExtractionResult(attachmentId, null);
    }
  }
}
