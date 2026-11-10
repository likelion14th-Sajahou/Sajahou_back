package com.sajarok.domain.attachment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TxtAttachmentExtractionServiceTest {

  @Mock private AttachmentService attachmentService;

  private TxtAttachmentExtractionService extractionService;

  @BeforeEach
  void setUp() {
    extractionService =
        new TxtAttachmentExtractionService(new TxtAttachmentExtractor(), attachmentService);
  }

  @Test
  void extractAndSave_passesUtf8TextToSave() {
    String body = "회의록 본문";
    var input = new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8));
    when(attachmentService.saveExtractionResult(501L, body)).thenReturn(true);

    assertThat(extractionService.extractAndSave(501L, input)).isTrue();

    verify(attachmentService).saveExtractionResult(501L, body);
  }

  @Test
  void extractAndSave_passesNullWhenUtf8IsInvalid() {
    var input = new ByteArrayInputStream(new byte[] {(byte) 0xC3, 0x28});
    when(attachmentService.saveExtractionResult(501L, null)).thenReturn(true);

    assertThat(extractionService.extractAndSave(501L, input)).isTrue();

    verify(attachmentService).saveExtractionResult(501L, null);
  }
}
