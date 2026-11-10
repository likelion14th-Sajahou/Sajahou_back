package com.sajarok.domain.project.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sajarok.domain.project.ai.FinishQuestionResult.Question;
import com.sajarok.domain.project.entity.Project;
import com.sajarok.domain.project.entity.ProjectRecord;
import com.sajarok.domain.project.entity.ProjectRecordStatus;
import com.sajarok.domain.project.entity.ProjectRole;
import com.sajarok.domain.question.dto.NewQuestion;
import com.sajarok.global.exception.BusinessException;
import com.sajarok.global.exception.ErrorCode;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 32번 AI 응답 검증과 프롬프트 조립 테스트. */
class FinishQuestionResultTest {

  private static Question q(String tag) {
    return new Question(tag, "이유", "질문");
  }

  @Test
  @DisplayName("32번 검증: 목록 안의 태그면 통과하고, H-17 태그 순서(내 역할 → … → 배운 점)로 정렬해 이유(quote)를 담는다")
  void validate_sortsByTagOrder() {
    List<NewQuestion> result =
        new FinishQuestionResult(List.of(q("배운 점"), q("산출물 요청"), q("내 역할"))).validate();

    assertThat(result).extracting(NewQuestion::tag).containsExactly("내 역할", "산출물 요청", "배운 점");
    assertThat(result).allMatch(n -> n.quote().equals("이유") && n.sourceAttachmentId() == null);
  }

  @Test
  @DisplayName("32번 검증: 빠진 항목이 없어 질문 0개인 것은 실패가 아니다")
  void validate_emptyIsOk() {
    assertThat(new FinishQuestionResult(List.of()).validate()).isEmpty();
  }

  @Test
  @DisplayName("32번 검증: 응답 없음·목록 밖 태그·같은 태그 두 번·빈 질문·빈 이유면 AI_FAILED")
  void validate_invalid_throwsAiFailed() {
    List<FinishQuestionResult> invalid = new ArrayList<>();
    invalid.add(new FinishQuestionResult(null));
    invalid.add(new FinishQuestionResult(List.of(q("주요 기여"))));
    invalid.add(new FinishQuestionResult(List.of(q("내 역할"), q("내 역할"))));
    invalid.add(new FinishQuestionResult(List.of(new Question("내 역할", "이유", " "))));
    invalid.add(new FinishQuestionResult(List.of(new Question("내 역할", null, "질문"))));

    for (FinishQuestionResult result : invalid) {
      assertThatThrownBy(result::validate)
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.AI_FAILED);
    }
  }

  @Test
  @DisplayName("프롬프트: 태그 목록·프로젝트·기록(빈 칸은 빠짐)이 이름표 구역으로 들어가고, system에 태그 5개와 최대 개수가 들어간다")
  void prompts_containSectionsAndTags() {
    Project project =
        Project.builder().userId(1L).name("사자록").roles(List.of(ProjectRole.BACKEND)).build();
    ProjectRecord record =
        ProjectRecord.builder()
            .project(project)
            .status(ProjectRecordStatus.SAVED)
            .did("팀 API 연동을 맡았다")
            .build();

    String user = FinishPrompts.questionsUser(project, List.of(record));

    assertThat(user)
        .contains("[태그 목록]", "[프로젝트]", "이름: 사자록", "역할: BACKEND", "[기록]", "한 일: 팀 API 연동을 맡았다")
        .doesNotContain("결과·반응:");
    assertThat(FinishPrompts.QUESTIONS_SYSTEM)
        .contains("내 역할 / 최종 결과 / 근거와 숫자 / 산출물 요청 / 배운 점", "최대 5개", "40%");
  }
}
