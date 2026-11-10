package com.sajarok.domain.attachment.service;

import com.sajarok.domain.attachment.entity.Attachment;
import com.sajarok.domain.attachment.entity.AttachmentCategory;
import com.sajarok.domain.attachment.entity.LinkType;
import com.sajarok.domain.attachment.repository.AttachmentRepository;
import com.sajarok.global.exception.BusinessException;
import com.sajarok.global.exception.ErrorCode;
import java.net.URI;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AttachmentService {

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
}
