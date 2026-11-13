package com.sajarok.domain.attachment.service;

import com.sajarok.infra.storage.S3AttachmentStorage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class AttachmentExtractionJob {

  private final S3AttachmentStorage storage;
  private final TxtAttachmentExtractionService txtExtractionService;
  private final PdfAttachmentExtractor pdfExtractor;
  private final AttachmentService attachmentService;
  private final ZipAttachmentExtractor zipExtractor;
  private final ImageAttachmentExtractor imageExtractor;

  @Async
  public void run(Long attachmentId, String s3Key, String extension) {
    try (var input = storage.open(s3Key)) {
      switch (extension) {
        case "txt", "md" -> txtExtractionService.extractAndSave(attachmentId, input);
        case "pdf" ->
            attachmentService.saveExtractionResult(attachmentId, pdfExtractor.extract(input));
        case "zip" ->
            attachmentService.saveExtractionResult(attachmentId, zipExtractor.extract(input));
        case "jpg", "png" -> {
          String text = imageExtractor.extract(input, extension);
          if (text.isBlank()) {
            attachmentService.saveImageWithoutText(attachmentId);
          } else {
            attachmentService.saveExtractionResult(attachmentId, text);
          }
        }
        default -> throw new IllegalArgumentException("지원하지 않는 추출 형식: " + extension);
      }
    } catch (Exception e) {
      // S3·AI 오류뿐 아니라 PDF 렌더링 같은 런타임 예외도 FAILED로 끝낸다. PENDING에 갇히면 AI 질문이 409로 막힌다
      log.warn("첨부 본문 읽기 실패 attachmentId={}", attachmentId, e);
      attachmentService.saveExtractionResult(attachmentId, null);
    }
  }
}
