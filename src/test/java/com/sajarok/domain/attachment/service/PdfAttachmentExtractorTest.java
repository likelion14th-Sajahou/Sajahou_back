package com.sajarok.domain.attachment.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.junit.jupiter.api.Test;

class PdfAttachmentExtractorTest {

  private final PdfAttachmentExtractor extractor = new PdfAttachmentExtractor();

  @Test
  void extract_returnsTextFromTextPdf() throws IOException {
    try (var document = new PDDocument()) {
      var page = new PDPage();
      document.addPage(page);

      try (var content = new PDPageContentStream(document, page)) {
        content.beginText();
        content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
        content.newLineAtOffset(50, 700);
        content.showText("Meeting notes");
        content.endText();
      }

      assertThat(extract(document)).isEqualTo("Meeting notes");
    }
  }

  @Test
  void extract_returnsEmptyTextFromImageOnlyPdf() throws IOException {
    try (var document = new PDDocument()) {
      var page = new PDPage();
      document.addPage(page);

      var image = new BufferedImage(20, 20, BufferedImage.TYPE_INT_RGB);
      var pdfImage = LosslessFactory.createFromImage(document, image);

      try (var content = new PDPageContentStream(document, page)) {
        content.drawImage(pdfImage, 50, 700);
      }

      assertThat(extract(document)).isEmpty();
    }
  }

  private String extract(PDDocument document) throws IOException {
    var output = new ByteArrayOutputStream();
    document.save(output);
    return extractor.extract(new ByteArrayInputStream(output.toByteArray()));
  }
}
