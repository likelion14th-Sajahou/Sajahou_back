package com.sajarok.domain.attachment.controller;

import com.sajarok.domain.attachment.dto.AttachmentLinkRequest;
import com.sajarok.domain.attachment.dto.AttachmentLinkResponse;
import com.sajarok.domain.attachment.dto.AttachmentStatusResponse;
import com.sajarok.domain.attachment.service.AttachmentService;
import com.sajarok.global.auth.CurrentUserProvider;
import com.sajarok.global.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
}
