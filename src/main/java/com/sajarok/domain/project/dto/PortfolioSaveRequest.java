package com.sajarok.domain.project.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 34번 포트폴리오 저장 요청. 10만 자 상한은 터무니없이 큰 요청을 막는 용도다(컬럼은 MEDIUMTEXT). */
public record PortfolioSaveRequest(
    @NotBlank(message = "포트폴리오 내용을 입력해 주세요.")
        @Size(max = 100000, message = "포트폴리오는 100,000자까지 저장할 수 있어요.")
        String markdown) {}
