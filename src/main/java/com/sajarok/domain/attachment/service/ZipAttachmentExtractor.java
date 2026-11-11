package com.sajarok.domain.attachment.service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ZipAttachmentExtractor {

  private static final int MAX_ENTRIES = 200;
  private static final long MAX_UNCOMPRESSED_BYTES = 20L * 1024 * 1024;

  private final TxtAttachmentExtractor txtExtractor;

  public String extract(InputStream input) throws IOException {
    StringBuilder result = new StringBuilder();
    byte[] buffer = new byte[8192];
    int entryCount = 0;
    long totalBytes = 0;

    try (var zip = new ZipInputStream(input)) {
      ZipEntry entry;
      while ((entry = zip.getNextEntry()) != null) {
        if (++entryCount > MAX_ENTRIES) {
          throw new IOException("ZIP 항목이 200개를 초과했습니다.");
        }

        boolean markdown =
            !entry.isDirectory() && entry.getName().toLowerCase(Locale.ROOT).endsWith(".md");
        var markdownBytes = new ByteArrayOutputStream();

        int bytesRead;
        while ((bytesRead = zip.read(buffer)) != -1) {
          totalBytes += bytesRead;
          if (totalBytes > MAX_UNCOMPRESSED_BYTES) {
            throw new IOException("ZIP 압축 해제 크기가 20MB를 초과했습니다.");
          }

          if (markdown) {
            markdownBytes.write(buffer, 0, bytesRead);
          }
        }

        if (markdown) {
          String text = txtExtractor.extract(new ByteArrayInputStream(markdownBytes.toByteArray()));
          if (!text.isBlank()) {
            if (result.length() > 0) {
              result.append("\n\n");
            }
            result.append(text);
          }
        }
      }
    }

    return result.toString();
  }
}
