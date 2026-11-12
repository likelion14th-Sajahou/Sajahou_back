package com.sajarok.domain.project.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sajarok.domain.project.entity.Project;
import com.sajarok.domain.project.entity.ProjectRecord;
import com.sajarok.domain.project.entity.ProjectRecordStatus;
import com.sajarok.domain.question.dto.QuestionView;
import com.sajarok.domain.question.entity.QuestionStatus;
import com.sajarok.global.exception.BusinessException;
import com.sajarok.global.exception.ErrorCode;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 31번 AI 응답 검증과 프롬프트 조립 테스트. */
class RecordOrganizeResultTest {

  @Test
  @DisplayName("31번 검증: 5칸을 did → nextStep 순서로 주고, 공백뿐인 칸은 null(근거 없음)로 본다")
  void validate_ordersAndTreatsBlankAsNull() {
    Map<String, String> result =
        new RecordOrganizeResult(" 한 일 ", "  ", null, "결과", null).validate();

    assertThat(result.keySet()).containsExactly("did", "concern", "decision", "result", "nextStep");
    assertThat(result.get("did")).isEqualTo("한 일");
    assertThat(result.get("concern")).isNull();
  }

  @Test
  @DisplayName("31번 검증: 한 칸이라도 10,000자를 넘으면 AI_FAILED (28번 칸 상한)")
  void validate_tooLong_throwsAiFailed() {
    String tooLong = "가".repeat(10001);

    assertThatThrownBy(() -> new RecordOrganizeResult(null, null, null, tooLong, null).validate())
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.AI_FAILED);
  }

  @Test
  @DisplayName("프롬프트: 메모·이미 쓴 칸·첨부·AI 질문 답이 이름표 구역으로 들어가고, 없는 구역은 '없음'")
  void organizeUser_containsSections() {
    ProjectRecord record =
        ProjectRecord.builder()
            .project(Project.builder().userId(1L).name("사자록").build())
            .status(ProjectRecordStatus.DRAFT)
            .memo("11/3 기획 회의. 홈 화면 구성 논의. 나는 시안 2개 준비")
            .build();
    QuestionView answered =
        new QuestionView(
            1L, 1, "내 역할", null, null, "질문", "홈 시안 2개를 준비해서 비교 발표했어요.", QuestionStatus.ANSWERED);

    String user = RecordPrompts.organizeUser(record, List.of(answered), List.of());

    assertThat(user)
        .contains(
            "[메모]\n11/3 기획 회의. 홈 화면 구성 논의. 나는 시안 2개 준비",
            "[이미 쓴 칸]\n없음",
            "[첨부]\n없음",
            "[AI 질문 답]\n- 내 역할: 홈 시안 2개를 준비해서 비교 발표했어요.");
    assertThat(RecordPrompts.ORGANIZE_SYSTEM).contains("20%", "\"nextStep\":null");
  }

  @Test
  @DisplayName("31번 프롬프트: 첨부 글자는 하나당 6,000자까지만 넣는다")
  void organizeUser_cutsLongAttachment() {
    ProjectRecord record =
        ProjectRecord.builder()
            .project(Project.builder().userId(1L).name("사자록").build())
            .status(ProjectRecordStatus.DRAFT)
            .build();
    String kept = "가".repeat(RecordPrompts.MAX_ATTACHMENT_CHARS);

    String user = RecordPrompts.organizeUser(record, List.of(), List.of(kept + "뒷부분", "짧은 첨부"));

    assertThat(user).contains("(1) " + kept + "\n", "(2) 짧은 첨부").doesNotContain("뒷부분");
  }
}
