package com.sajarok.domain.project.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * 32번 마치기 질문 요청. recordIds는 마치기 1단계(H-16)에서 고른 기록.
 *
 * <p>50개 상한은 명세에 없어 정한 값이다(H-16이 모든 기록을 고른 채 시작하므로 넉넉하게, 프롬프트가 너무 길어지지 않게).
 */
public record FinishQuestionsRequest(
    @NotEmpty(message = "기록을 하나 이상 골라 주세요.") @Size(max = 50, message = "기록은 50개까지 고를 수 있어요.")
        List<@NotNull Long> recordIds) {}
