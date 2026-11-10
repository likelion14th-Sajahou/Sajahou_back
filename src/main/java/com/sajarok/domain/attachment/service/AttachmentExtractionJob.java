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
  private final ZipAttachmentExtractor zipExtractor;

  @Async
  public void run(Long attachmentId, String s3Key, String extension) {
    try (var input = storage.open(s3Key)) {
      switch (extension) {
        case "txt", "md" -> txtExtractionService.extractAndSave(attachmentId, input);
        case "pdf" ->
            attachmentService.saveExtractionResult(attachmentId, pdfExtractor.extract(input));
        case "zip" ->
            attachmentService.saveExtractionResult(attachmentId, zipExtractor.extract(input));
        default -> throw new IllegalArgumentException("지원하지 않는 추출 형식: " + extension);
      }
    } catch (IOException | SdkException e) {
      log.warn("첨부 본문 읽기 실패 attachmentId={}", attachmentId, e);
      attachmentService.saveExtractionResult(attachmentId, null);
    }
  }
}
