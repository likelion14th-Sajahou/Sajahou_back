package com.sajarok.domain.project.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.sajarok.domain.project.ai.RecordQuestionResult.Validated;
import com.sajarok.domain.project.entity.Project;
import com.sajarok.domain.project.entity.ProjectRecord;
import com.sajarok.domain.project.entity.ProjectRecordStatus;
import com.sajarok.domain.question.dto.NewQuestion;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 30번 RecordQuestionGenerator 테스트 (AiClient 연결 전). */
class RecordQuestionGeneratorTest {

  @Test
  @DisplayName("AiClient 연결 전: H-13 프로토타입 고정 질문 4개(태그 3종, 근거 문장 없음)가 검증을 통과한다")
  void generate_beforeAiClient_returnsPrototypeQuestions() {
    ProjectRecord record =
        ProjectRecord.builder()
            .project(Project.builder().userId(1L).name("사자록").build())
            .status(ProjectRecordStatus.DRAFT)
            .memo("11/3 기획 회의")
            .build();

    Validated v = new RecordQuestionGenerator().generate(record, List.of());

    assertThat(v.insufficientMaterial()).isFalse();
    assertThat(v.questions())
        .extracting(NewQuestion::tag)
        .containsExactly("내 역할·과정", "내 역할·과정", "선택의 이유", "근거와 숫자");
    assertThat(v.questions()).allMatch(q -> q.quote() == null && q.sourceAttachmentId() == null);
  }
}
