package com.sajarok.domain.attachment.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;

class ZipAttachmentExtractorTest {

  private final ZipAttachmentExtractor extractor =
      new ZipAttachmentExtractor(new TxtAttachmentExtractor());

  @Test
  void extract_combinesMarkdownAndSkipsOtherFiles() throws IOException {
    var output = new ByteArrayOutputStream();
    try (var zip = new ZipOutputStream(output)) {
      addEntry(zip, "회의록.md", "# 회의록");
      addEntry(zip, "images/capture.png", "본문에 들어가면 안 되는 데이터");
      addEntry(zip, "notes/발표.md", "# 발표 자료");
    }

    String text = extractor.extract(new ByteArrayInputStream(output.toByteArray()));

    assertThat(text).isEqualTo("# 회의록\n\n# 발표 자료");
  }

  @Test
  void extract_returnsEmptyTextWhenZipHasNoMarkdown() throws IOException {
    var output = new ByteArrayOutputStream();
    try (var zip = new ZipOutputStream(output)) {
      addEntry(zip, "notes.txt", "TXT 본문");
    }

    String text = extractor.extract(new ByteArrayInputStream(output.toByteArray()));

    assertThat(text).isEmpty();
  }

  private static void addEntry(ZipOutputStream zip, String name, String content)
      throws IOException {
    zip.putNextEntry(new ZipEntry(name));
    zip.write(content.getBytes(StandardCharsets.UTF_8));
    zip.closeEntry();
  }
}
