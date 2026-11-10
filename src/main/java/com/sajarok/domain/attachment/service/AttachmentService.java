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
import java.net.URI;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AttachmentService {

  private static final long EXTRACTION_TIMEOUT_NANOS = TimeUnit.SECONDS.toNanos(10);
  private static final long EXTRACTION_POLL_NANOS = TimeUnit.MILLISECONDS.toNanos(250);

  private final AttachmentRepository attachmentRepository;

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
}
