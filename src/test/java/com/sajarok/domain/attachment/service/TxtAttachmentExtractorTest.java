package com.sajarok.domain.attachment.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class TxtAttachmentExtractorTest {

  @Test
  void extract_readsUtf8TextAndRemovesBom() throws IOException {
    String body = "회의록\n다음 주까지 API 명세를 정리하기로 했어요.";
    byte[] bytes = ("\uFEFF" + body).getBytes(StandardCharsets.UTF_8);

    String extracted = new TxtAttachmentExtractor().extract(new ByteArrayInputStream(bytes));

    assertThat(extracted).isEqualTo(body);
  }
}
