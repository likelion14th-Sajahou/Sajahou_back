package com.sajarok.domain.attachment.service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import org.springframework.stereotype.Component;

@Component
public class TxtAttachmentExtractor {

  private static final int MAX_FILE_BYTES = 20 * 1024 * 1024;

  public String extract(InputStream input) throws IOException {
    byte[] bytes = input.readNBytes(MAX_FILE_BYTES + 1);
    if (bytes.length > MAX_FILE_BYTES) {
      throw new IOException("TXT 파일이 20MB를 초과했습니다.");
    }

    String text = StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes)).toString();
    return text.startsWith("\uFEFF") ? text.substring(1) : text;
  }
}
