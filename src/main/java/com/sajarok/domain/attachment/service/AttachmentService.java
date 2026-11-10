package com.sajarok.domain.attachment.service;

import com.sajarok.domain.attachment.entity.Attachment;
import com.sajarok.domain.attachment.entity.AttachmentCategory;
import com.sajarok.domain.attachment.entity.LinkType;
import com.sajarok.domain.attachment.repository.AttachmentRepository;
import com.sajarok.global.exception.BusinessException;
import com.sajarok.global.exception.ErrorCode;
import com.sajarok.infra.storage.S3AttachmentStorage;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AttachmentService {

  private final AttachmentRepository attachmentRepository;
  private static final long MAX_FILE_SIZE = 20L * 1024 * 1024;
  private static final Set<String> ALLOWED_EXTENSIONS =
      Set.of("pdf", "png", "jpg", "zip", "md", "txt");

  private final S3AttachmentStorage storage;

  @Transactional
  public Attachment registerLink(Long userId, String url, AttachmentCategory category) {
    URI uri = parseLink(url);
    LinkType linkType = classify(uri.getHost().toLowerCase(Locale.ROOT));
    return attachmentRepository.save(Attachment.createLink(userId, category, url, linkType));
  }

  public Attachment getOwnedAttachment(Long userId, Long attachmentId) {
    return attachmentRepository
        .findByIdAndUserId(attachmentId, userId)
        .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
  }

  private static URI parseLink(String url) {
    if (url != null && url.length() <= 2048) {
      try {
        URI uri = URI.create(url);
        if (uri.getHost() != null
            && uri.getUserInfo() == null
            && ("https".equalsIgnoreCase(uri.getScheme())
                || "http".equalsIgnoreCase(uri.getScheme()))) {
          return uri;
        }
      } catch (IllegalArgumentException ignored) {
        // 잘못된 URL은 아래에서 동일한 입력 오류로 처리한다.
      }
    }
    throw new BusinessException(ErrorCode.VALIDATION_ERROR, "올바른 HTTP 또는 HTTPS 링크를 입력해 주세요.");
  }

  private static LinkType classify(String host) {
    if (matchesDomain(host, "figma.com")) {
      return LinkType.FIGMA;
    }
    if (matchesDomain(host, "github.com")) {
      return LinkType.GITHUB;
    }
    if (matchesDomain(host, "notion.so") || matchesDomain(host, "notion.site")) {
      return LinkType.NOTION;
    }
    return LinkType.OTHER;
  }

  private static boolean matchesDomain(String host, String domain) {
    return host.equals(domain) || host.endsWith("." + domain);
  }

  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  public Attachment registerFile(Long userId, MultipartFile file, AttachmentCategory category) {
    String name = file.getOriginalFilename();
    if (file.isEmpty() || file.getSize() > MAX_FILE_SIZE) {
      throw new BusinessException(ErrorCode.VALIDATION_ERROR, "파일은 비어 있지 않아야 하고 20MB까지 올릴 수 있어요.");
    }
    if (name == null || name.isBlank() || name.length() > 255) {
      throw new BusinessException(ErrorCode.VALIDATION_ERROR, "파일 이름을 확인해 주세요.");
    }

    int dot = name.lastIndexOf('.');
    if (dot < 0 || !ALLOWED_EXTENSIONS.contains(name.substring(dot + 1).toLowerCase(Locale.ROOT))) {
      throw new BusinessException(ErrorCode.VALIDATION_ERROR, "지원하지 않는 파일 형식이에요.");
    }

    String key = "attachments/" + UUID.randomUUID();
    String contentType = file.getContentType();
    if (contentType == null || contentType.isBlank() || contentType.length() > 100) {
      contentType = "application/octet-stream";
    }

    Attachment attachment =
        Attachment.createFile(userId, category, name, key, contentType, file.getSize());

    boolean uploaded = false;
    try (var input = file.getInputStream()) {
      storage.upload(key, input, file.getSize(), contentType);
      uploaded = true;
    } catch (IOException e) {
      if (uploaded) {
        deleteAfterFailure(key, e);
      }
      throw new UncheckedIOException("첨부 파일을 처리하지 못했습니다.", e);
    }

    try {
      return attachmentRepository.save(attachment);
    } catch (RuntimeException e) {
      deleteAfterFailure(key, e);
      throw e;
    }
  }

  private void deleteAfterFailure(String key, Exception originalError) {
    try {
      storage.delete(key);
    } catch (RuntimeException cleanupError) {
      originalError.addSuppressed(cleanupError);
      log.warn("업로드 실패 후 S3 파일 정리 실패 key={}", key, cleanupError);
    }
  }
}
