package com.sajarok.domain.attachment.controller;

import com.sajarok.domain.attachment.dto.AttachmentFileResponse;
import com.sajarok.domain.attachment.dto.AttachmentLinkRequest;
import com.sajarok.domain.attachment.dto.AttachmentLinkResponse;
import com.sajarok.domain.attachment.dto.AttachmentStatusResponse;
import com.sajarok.domain.attachment.entity.AttachmentCategory;
import com.sajarok.domain.attachment.entity.ExtractStatus;
import com.sajarok.domain.attachment.service.AttachmentExtractionJob;
import com.sajarok.domain.attachment.service.AttachmentService;
import com.sajarok.domain.attachment.service.LinkAttachmentExtractionJob;
import com.sajarok.global.auth.CurrentUserProvider;
import com.sajarok.global.response.ApiResponse;
import jakarta.validation.Valid;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/attachments")
@RequiredArgsConstructor
public class AttachmentController {

  private final AttachmentService attachmentService;
  private final CurrentUserProvider currentUserProvider;
  private final AttachmentExtractionJob extractionJob;
  private final LinkAttachmentExtractionJob linkExtractionJob;

  @PostMapping("/links")
  public ResponseEntity<ApiResponse<AttachmentLinkResponse>> registerLink(
      @Valid @RequestBody AttachmentLinkRequest request) {
    var attachment =
        attachmentService.registerLink(
            currentUserProvider.getUserId(), request.url(), request.category());

    if (attachment.getExtractStatus() == ExtractStatus.PENDING) {
      linkExtractionJob.run(attachment.getId(), attachment.getUrl(), attachment.getLinkType());
    }

    return ResponseEntity.status(HttpStatus.CREATED)
        .body(ApiResponse.ok(AttachmentLinkResponse.from(attachment)));
  }

  @GetMapping("/{attachmentId}")
  public ApiResponse<AttachmentStatusResponse> getStatus(@PathVariable Long attachmentId) {
    var attachment =
        attachmentService.getOwnedAttachment(currentUserProvider.getUserId(), attachmentId);

    return ApiResponse.ok(AttachmentStatusResponse.from(attachment));
  }

  @PostMapping(path = "/files", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ResponseEntity<ApiResponse<AttachmentFileResponse>> registerFile(
      @RequestParam("file") MultipartFile file,
      @RequestParam("category") AttachmentCategory category) {
    var attachment =
        attachmentService.registerFile(currentUserProvider.getUserId(), file, category);
    String name = attachment.getName().toLowerCase(Locale.ROOT);
    String extension = name.substring(name.lastIndexOf('.') + 1);

    switch (extension) {
      case "txt", "md", "pdf", "zip" ->
          extractionJob.run(attachment.getId(), attachment.getS3Key(), extension);
      default -> {
        // 현재 추출을 지원하는 형식만 작업을 시작한다.
      }
    }

    return ResponseEntity.status(HttpStatus.CREATED)
        .body(ApiResponse.ok(AttachmentFileResponse.from(attachment)));
  }

  @DeleteMapping("/{attachmentId}")
  public ResponseEntity<Void> delete(@PathVariable Long attachmentId) {
    attachmentService.delete(currentUserProvider.getUserId(), attachmentId);
    return ResponseEntity.noContent().build();
  }
}
