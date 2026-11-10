package com.sajarok.domain.attachment.service;

import com.sajarok.domain.attachment.dto.AttachmentOwner;
import com.sajarok.domain.attachment.dto.AttachmentView;
import com.sajarok.domain.attachment.entity.Attachment;
import com.sajarok.domain.attachment.entity.AttachmentCategory;
import com.sajarok.domain.attachment.entity.ExtractStatus;
import com.sajarok.domain.attachment.entity.LinkType;
import com.sajarok.domain.attachment.repository.AttachmentRepository;
import com.sajarok.global.exception.BusinessException;
import com.sajarok.global.exception.ErrorCode;
import com.sajarok.infra.storage.S3AttachmentStorage;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
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

  private static final long EXTRACTION_TIMEOUT_NANOS = TimeUnit.SECONDS.toNanos(10);
  private static final long EXTRACTION_POLL_NANOS = TimeUnit.MILLISECONDS.toNanos(250);

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

  @Transactional
  public void connect(Long userId, List<Long> ids, AttachmentOwner owner) {
    if (ids == null || ids.isEmpty()) {
      return;
    }

    var distinctIds = new LinkedHashSet<>(ids);
    var attachments = attachmentRepository.findAllByUserIdAndIdInOrderByIdAsc(userId, distinctIds);

    if (attachments.size() != distinctIds.size()) {
      throw new BusinessException(ErrorCode.NOT_FOUND);
    }

    if (attachments.stream().anyMatch(attachment -> !attachment.canConnectTo(owner))) {
      throw new BusinessException(ErrorCode.INVALID_STATE, "이미 다른 곳에 연결된 첨부가 있어요.");
    }

    attachments.forEach(attachment -> attachment.connectTo(owner));
  }

  public List<AttachmentView> findAll(AttachmentOwner owner) {
    return findAll(owner.kind(), List.of(owner.id())).getOrDefault(owner.id(), List.of());
  }

  public Map<Long, List<AttachmentView>> findAll(
      AttachmentOwner.Kind kind, Collection<Long> ownerIds) {
    if (ownerIds.isEmpty()) {
      return Map.of();
    }

    List<Attachment> attachments = findAttachments(kind, ownerIds);

    return attachments.stream()
        .collect(
            Collectors.groupingBy(
                attachment -> ownerIdOf(attachment, kind),
                Collectors.mapping(AttachmentView::from, Collectors.toList())));
  }

  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  public List<String> readTexts(AttachmentOwner owner) {
    List<Long> ownerIds = List.of(owner.id());
    long deadline = System.nanoTime() + EXTRACTION_TIMEOUT_NANOS;

    while (true) {
      List<Attachment> attachments = findAttachments(owner.kind(), ownerIds);
      boolean hasPending =
          attachments.stream().anyMatch(a -> a.getExtractStatus() == ExtractStatus.PENDING);

      if (!hasPending) {
        return attachments.stream()
            .filter(a -> a.getExtractStatus() == ExtractStatus.DONE)
            .map(Attachment::getExtractedText)
            .filter(text -> text != null && !text.isBlank())
            .toList();
      }

      long remaining = deadline - System.nanoTime();
      if (remaining <= 0) {
        throw new BusinessException(ErrorCode.ATTACHMENT_PENDING);
      }

      try {
        TimeUnit.NANOSECONDS.sleep(Math.min(remaining, EXTRACTION_POLL_NANOS));
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new BusinessException(ErrorCode.ATTACHMENT_PENDING);
      }
    }
  }

  private List<Attachment> findAttachments(AttachmentOwner.Kind kind, Collection<Long> ownerIds) {
    return switch (kind) {
      case RETROSPECTIVE -> attachmentRepository.findAllByRetrospectiveIdInOrderByIdAsc(ownerIds);
      case PROJECT_RECORD -> attachmentRepository.findAllByProjectRecordIdInOrderByIdAsc(ownerIds);
      case PROJECT -> attachmentRepository.findAllByProjectIdInOrderByIdAsc(ownerIds);
      case JOB_POSTING -> attachmentRepository.findAllByJobPostingIdInOrderByIdAsc(ownerIds);
    };
  }

  private static Long ownerIdOf(Attachment attachment, AttachmentOwner.Kind kind) {
    return switch (kind) {
      case RETROSPECTIVE -> attachment.getRetrospectiveId();
      case PROJECT_RECORD -> attachment.getProjectRecordId();
      case PROJECT -> attachment.getProjectId();
      case JOB_POSTING -> attachment.getJobPostingId();
    };
  }

  @Transactional
  public boolean saveExtractionResult(Long attachmentId, String extractedText) {
    boolean hasText = extractedText != null && !extractedText.isBlank();

    return attachmentRepository.updateExtractionResult(
            attachmentId,
            hasText ? extractedText : null,
            hasText ? ExtractStatus.DONE : ExtractStatus.FAILED,
            ExtractStatus.PENDING,
            LocalDateTime.now())
        == 1;
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
