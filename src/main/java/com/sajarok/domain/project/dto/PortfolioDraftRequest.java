package com.sajarok.domain.project.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/** 33번 포트폴리오 초안 요청. recordIds는 마치기 1단계(H-16)에서 고른 기록 (32번과 같은 1 ~ 50개 기준). */
public record PortfolioDraftRequest(
    @NotEmpty(message = "기록을 하나 이상 골라 주세요.") @Size(max = 50, message = "기록은 50개까지 고를 수 있어요.")
        List<@NotNull Long> recordIds) {}
