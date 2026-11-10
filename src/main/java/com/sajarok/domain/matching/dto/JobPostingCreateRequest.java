package com.sajarok.domain.matching.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.stream.Stream;

/**
 * 36번 공고 저장 요청.
 *
 * <p>회사·부서 100자는 VARCHAR(100), 공고 칸 10,000자는 TEXT 컬럼 보호용 상한이다. 공고 4칸과 첨부가 모두 비었는지는 서비스에서
 * EMPTY_MATERIAL로 막는다(27번과 같은 기준).
 */
public record JobPostingCreateRequest(
    @Size(max = 100, message = "회사는 100자까지 쓸 수 있어요.") String company,
    @Size(max = 100, message = "부서는 100자까지 쓸 수 있어요.") String department,
    @Size(max = 10000, message = "공고 본문은 10,000자까지 쓸 수 있어요.") String postingText,
    @Size(max = 10000, message = "주요 업무·자격 요건은 10,000자까지 쓸 수 있어요.") String jd,
    @Size(max = 10000, message = "우대사항은 10,000자까지 쓸 수 있어요.") String preferredQualification,
    @Size(max = 10000, message = "기업 정보·인재상은 10,000자까지 쓸 수 있어요.") String companyInfo,
    List<@NotNull Long> attachmentIds,
    @NotEmpty(message = "문항을 하나 이상 넣어 주세요.") @Size(max = 10, message = "문항은 10개까지 넣을 수 있어요.")
        List<@NotNull @Valid QuestionRequest> questions) {

  /** 문항 하나. charLimit은 없으면 제한 없음. */
  public record QuestionRequest(
      @NotBlank(message = "문항을 써 주세요.") @Size(max = 2000, message = "문항은 2,000자까지 쓸 수 있어요.")
          String content,
      @Min(value = 1, message = "글자 수 제한은 1 이상이에요.")
          @Max(value = 10000, message = "글자 수 제한은 10,000까지예요.")
          Integer charLimit) {}

  public boolean hasAttachments() {
    return attachmentIds != null && !attachmentIds.isEmpty();
  }

  /** 공고 4칸(본문·JD·우대사항·기업 정보) 중 글자가 있는 칸이 있거나 첨부가 있는지. 회사·부서는 세지 않는다. */
  public boolean hasMaterial() {
    return hasAttachments()
        || Stream.of(postingText, jd, preferredQualification, companyInfo)
            .anyMatch(v -> v != null && !v.isBlank());
  }
}
