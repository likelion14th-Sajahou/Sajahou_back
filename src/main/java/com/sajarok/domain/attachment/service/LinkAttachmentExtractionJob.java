package com.sajarok.domain.attachment.service;

import com.sajarok.domain.attachment.entity.LinkType;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class LinkAttachmentExtractionJob {

  // /tree/main 같은 경로가 붙어도 앞의 소유자와 저장소 이름만 사용한다.
  private static final Pattern REPOSITORY_PATH =
          Pattern.compile("/([A-Za-z0-9-]+)/([A-Za-z0-9_.-]+?)(?:\\.git)?(?:/.*)?");
  private static final int MAX_README_BYTES = 128 * 1024;

  private final AttachmentService attachmentService;

  @Async
  public void run(Long attachmentId, String url, LinkType linkType) {
    String text = null;
    try {
      if (linkType == LinkType.GITHUB) {
        text = readGitHubReadme(URI.create(url));
      }
    } catch (IOException | IllegalArgumentException e) {
      log.warn("링크 본문 추출 실패 attachmentId={}", attachmentId, e);
    }

    attachmentService.saveExtractionResult(attachmentId, text);
  }

  private static String readGitHubReadme(URI link) throws IOException {
    if (!"github.com".equalsIgnoreCase(link.getHost())) {
      return null;
    }

    var path = REPOSITORY_PATH.matcher(link.getRawPath());
    if (!path.matches()) {
      return null;
    }

    String repository = path.group(2);
    if (repository.equals(".") || repository.equals("..")) {
      return null;
    }

    URI endpoint =
        URI.create("https://api.github.com/repos/" + path.group(1) + "/" + repository + "/readme");
    var connection = (HttpURLConnection) endpoint.toURL().openConnection();
    connection.setInstanceFollowRedirects(false);
    connection.setConnectTimeout(2_000);
    connection.setReadTimeout(4_000);
    connection.setRequestProperty("Accept", "application/vnd.github.raw+json");
    connection.setRequestProperty("User-Agent", "sajarok");

    try {
      if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
        return null;
      }

      try (var input = connection.getInputStream()) {
        byte[] body = input.readNBytes(MAX_README_BYTES + 1);
        return body.length > MAX_README_BYTES ? null : new String(body, StandardCharsets.UTF_8);
      }
    } finally {
      connection.disconnect();
    }
  }
}
