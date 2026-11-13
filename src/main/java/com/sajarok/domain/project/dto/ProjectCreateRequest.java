package com.sajarok.domain.project.dto;

import com.sajarok.domain.project.entity.ProjectCategory;
import com.sajarok.domain.project.entity.ProjectRole;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * 25번 프로젝트 생성 요청.
 *
 * @param category 행사 종류. IDEATHON·HACKATHON·JUNGKATHON 중 하나 (필수)
 */
public record ProjectCreateRequest(
    @NotBlank(message = "프로젝트 이름을 입력해 주세요.") @Size(max = 100, message = "프로젝트 이름은 100자까지 쓸 수 있어요.")
        String name,
    @Size(max = 100, message = "부제는 100자까지 쓸 수 있어요.") String subtitle,
    @NotNull(message = "아이디어톤·해커톤·중커톤 중 하나를 골라 주세요.") ProjectCategory category,
    @NotEmpty(message = "내 역할을 하나 이상 골라 주세요.") List<@NotNull ProjectRole> roles) {}
