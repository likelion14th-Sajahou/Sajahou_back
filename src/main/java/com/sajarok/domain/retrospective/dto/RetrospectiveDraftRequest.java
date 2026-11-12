package com.sajarok.domain.retrospective.dto;

import com.sajarok.domain.retrospective.entity.AssignmentMode;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.AccessLevel;
import lombok.Getter;

/** 14번 임시저장. 칸을 아예 빼면 그대로, null을 보내면 비운다. */
@Getter
public class RetrospectiveDraftRequest {

  @Getter(AccessLevel.NONE)
  private final Set<String> sent = new HashSet<>();

  @Min(value = 2, message = "단계는 2~4만 보낼 수 있어요.")
  @Max(value = 4, message = "단계는 2~4만 보낼 수 있어요.")
  private Integer currentStep;

  private Boolean hasPractice;

  @Size(max = 10000, message = "실습 내용은 10,000자까지 쓸 수 있어요.")
  private String practiceContent;

  private AssignmentMode assignmentMode;

  @Size(max = 100000, message = "과제 내용은 100,000자까지 쓸 수 있어요.")
  private String assignmentContent;

  @Size(max = 10000, message = "코드리뷰는 10,000자까지 쓸 수 있어요.")
  private String codeReview;

  @Size(max = 10000, message = "메모는 10,000자까지 쓸 수 있어요.")
  private String memo;

  private List<Long> attachmentIds;

  /** 이 칸이 JSON에 들어 있었는지 */
  public boolean has(String field) {
    return sent.contains(field);
  }

  public void setCurrentStep(Integer currentStep) {
    this.currentStep = currentStep;
    sent.add("currentStep");
  }

  public void setHasPractice(Boolean hasPractice) {
    this.hasPractice = hasPractice;
    sent.add("hasPractice");
  }

  public void setPracticeContent(String practiceContent) {
    this.practiceContent = practiceContent;
    sent.add("practiceContent");
  }

  public void setAssignmentMode(AssignmentMode assignmentMode) {
    this.assignmentMode = assignmentMode;
    sent.add("assignmentMode");
  }

  public void setAssignmentContent(String assignmentContent) {
    this.assignmentContent = assignmentContent;
    sent.add("assignmentContent");
  }

  public void setCodeReview(String codeReview) {
    this.codeReview = codeReview;
    sent.add("codeReview");
  }

  public void setMemo(String memo) {
    this.memo = memo;
    sent.add("memo");
  }

  public void setAttachmentIds(List<Long> attachmentIds) {
    this.attachmentIds = attachmentIds;
    sent.add("attachmentIds");
  }
}
