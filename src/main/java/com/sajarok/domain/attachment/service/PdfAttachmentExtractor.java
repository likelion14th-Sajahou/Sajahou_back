package com.sajarok.domain.attachment.service;

import java.io.IOException;
import java.io.InputStream;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

@Component
public class PdfAttachmentExtractor {

  private static final int MAX_FILE_BYTES = 20 * 1024 * 1024;

  public String extract(InputStream input) throws IOException {
    byte[] bytes = input.readNBytes(MAX_FILE_BYTES + 1);
    if (bytes.length > MAX_FILE_BYTES) {
      throw new IOException("PDF 파일이 20MB를 초과했습니다.");
    }

    try (var document = Loader.loadPDF(bytes)) {
      return new PDFTextStripper().getText(document).strip();
    }
  }
}
