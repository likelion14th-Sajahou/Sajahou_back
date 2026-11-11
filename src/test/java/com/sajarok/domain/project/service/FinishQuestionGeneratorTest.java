package com.sajarok.domain.project.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.sajarok.domain.project.ai.FinishAttachments;
import com.sajarok.domain.project.entity.Project;
import com.sajarok.domain.project.entity.ProjectRecord;
import com.sajarok.domain.project.entity.ProjectRecordStatus;
import com.sajarok.domain.question.dto.NewQuestion;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 32번 FinishQuestionGenerator 테스트 (AiClient 연결 전). */
class FinishQuestionGeneratorTest {

  @Test
  @DisplayName("AiClient 연결 전: H-17 프로토타입 고정 질문 5개가 검증을 통과해 태그 순서대로, 빈 곳 이유와 함께 나온다")
  void generate_beforeAiClient_returnsPrototypeQuestions() {
    Project project = Project.builder().userId(1L).name("사자록").build();
    ProjectRecord record =
        ProjectRecord.builder()
            .project(project)
            .status(ProjectRecordStatus.SAVED)
            .did("API 연동")
            .build();

    List<NewQuestion> questions =
        new FinishQuestionGenerator().generate(project, List.of(record), FinishAttachments.none());

    assertThat(questions)
        .extracting(NewQuestion::tag)
        .containsExactly("내 역할", "최종 결과", "근거와 숫자", "산출물 요청", "배운 점");
    assertThat(questions.get(3).quote()).isEqualTo("최종 산출물이 아직 첨부되지 않았어요");
    assertThat(questions.get(4).content()).isEqualTo("다음 프로젝트에 꼭 가져갈 한 가지는요?");
  }
}
