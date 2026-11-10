package com.sajarok.domain.project.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.sajarok.domain.project.entity.Project;
import com.sajarok.domain.project.entity.ProjectRecord;
import com.sajarok.domain.project.entity.ProjectRecordStatus;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 31번 RecordOrganizer 테스트 (AiClient 연결 전). */
class RecordOrganizerTest {

  @Test
  @DisplayName("AiClient 연결 전: 5칸 모두 제안 없음(null)으로 검증을 통과해 key 순서대로 나온다")
  void organize_beforeAiClient_allNull() {
    ProjectRecord record =
        ProjectRecord.builder()
            .project(Project.builder().userId(1L).name("사자록").build())
            .status(ProjectRecordStatus.DRAFT)
            .memo("메모")
            .build();

    Map<String, String> result = new RecordOrganizer().organize(record, List.of(), List.of());

    assertThat(result.keySet()).containsExactly("did", "concern", "decision", "result", "nextStep");
    assertThat(result.values()).containsOnlyNulls();
  }
}
