package com.sajarok.domain.attachment.dto;

import com.sajarok.domain.attachment.entity.AttachmentCategory;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AttachmentLinkRequest(
    @NotBlank(message = "링크를 입력해 주세요.") @Size(max = 2048, message = "링크는 2,048자까지 입력할 수 있어요.")
        String url,
    @NotNull(message = "자료 종류를 골라 주세요.") AttachmentCategory category) {}
