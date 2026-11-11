package com.sajarok.domain.attachment.controller;

import com.sajarok.domain.attachment.dto.AttachmentFileResponse;
import com.sajarok.domain.attachment.dto.AttachmentLinkRequest;
import com.sajarok.domain.attachment.dto.AttachmentLinkResponse;
import com.sajarok.domain.attachment.dto.AttachmentStatusResponse;
import com.sajarok.domain.attachment.entity.AttachmentCategory;
import com.sajarok.domain.attachment.service.AttachmentService;
import com.sajarok.global.auth.CurrentUserProvider;
import com.sajarok.global.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/attachments")
@RequiredArgsConstructor
public class AttachmentController {

  private final AttachmentService attachmentService;
  private final CurrentUserProvider currentUserProvider;

  @PostMapping("/links")
  public ResponseEntity<ApiResponse<AttachmentLinkResponse>> registerLink(
      @Valid @RequestBody AttachmentLinkRequest request) {
    var attachment =
        attachmentService.registerLink(
            currentUserProvider.getUserId(), request.url(), request.category());

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

    return ResponseEntity.status(HttpStatus.CREATED)
        .body(ApiResponse.ok(AttachmentFileResponse.from(attachment)));
  }
}
