package com.sajarok.domain.attachment.service;

import com.sajarok.global.exception.BusinessException;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import javax.imageio.ImageIO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class PdfAttachmentExtractor {

  private static final int MAX_FILE_BYTES = 20 * 1024 * 1024;

  private static final int MIN_TEXT_CHARACTERS = 12;
  private static final int MAX_OCR_PAGES = 3;
  private static final int MAX_RENDER_SIDE = 1600;
  private final ImageAttachmentExtractor imageExtractor;

  public String extract(InputStream input) throws IOException {
    byte[] bytes = input.readNBytes(MAX_FILE_BYTES + 1);
    if (bytes.length > MAX_FILE_BYTES) {
      throw new IOException("PDF 파일이 20MB를 초과했습니다.");
    }

    try (var document = Loader.loadPDF(bytes)) {
      var stripper = new PDFTextStripper();
      var renderer = new PDFRenderer(document);
      var result = new StringBuilder();
      int ocrPages = 0;
      for (int index = 0; index < document.getNumberOfPages(); index++) {
        int pageNumber = index + 1;
        stripper.setStartPage(pageNumber);
        stripper.setEndPage(pageNumber);
        String pageText = stripper.getText(document).strip();
        var page = document.getPage(index);
        if (ocrPages < MAX_OCR_PAGES
            && page.hasContents()
            && pageText.codePoints().filter(c -> !Character.isWhitespace(c)).count()
                < MIN_TEXT_CHARACTERS) {
          ocrPages++;
          var box = page.getCropBox();
          float longestSide = Math.max(box.getWidth(), box.getHeight());
          float scale = Math.min(2f, MAX_RENDER_SIDE / Math.max(1f, longestSide));
          try {
            var image = renderer.renderImage(index, scale);
            var png = new ByteArrayOutputStream();
            if (!ImageIO.write(image, "png", png)) {
              throw new IOException("PNG 인코더를 찾을 수 없습니다.");
            }
            String recognized =
                imageExtractor.extract(new ByteArrayInputStream(png.toByteArray()), "png");
            if (!recognized.isBlank()) {
              pageText = recognized;
            }
          } catch (IOException | BusinessException e) {
            log.warn("PDF 페이지 OCR 실패 page={}", pageNumber, e);
          }
        }
        if (!pageText.isBlank()) {
          if (!result.isEmpty()) {
            result.append("\n\n");
          }
          result.append(pageText);
        }
      }
      return result.toString();
    }
  }
}
