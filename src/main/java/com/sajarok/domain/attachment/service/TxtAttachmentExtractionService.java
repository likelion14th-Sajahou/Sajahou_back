package com.sajarok.domain.attachment.service;

import java.io.IOException;
import java.io.InputStream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class TxtAttachmentExtractionService {

  private final TxtAttachmentExtractor extractor;
  private final AttachmentService attachmentService;

  public boolean extractAndSave(Long attachmentId, InputStream input) {
    String text;
    try {
      text = extractor.extract(input);
    } catch (IOException e) {
      log.warn("TXT 첨부 본문 읽기 실패 attachmentId={}", attachmentId, e);
      return attachmentService.saveExtractionResult(attachmentId, null);
    }

    return attachmentService.saveExtractionResult(attachmentId, text);
  }
}
