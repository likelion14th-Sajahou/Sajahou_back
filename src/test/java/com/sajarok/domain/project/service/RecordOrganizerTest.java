package com.sajarok.domain.project.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.sajarok.domain.project.ai.RecordOrganizeResult;
import com.sajarok.domain.project.ai.RecordPrompts;
import com.sajarok.domain.project.entity.Project;
import com.sajarok.domain.project.entity.ProjectRecord;
import com.sajarok.domain.project.entity.ProjectRecordStatus;
import com.sajarok.global.exception.BusinessException;
import com.sajarok.global.exception.ErrorCode;
import com.sajarok.infra.ai.AiClient;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** 31번 RecordOrganizer 테스트. 실제 OpenAI 대신 가짜 AiClient를 쓴다. */
@ExtendWith(MockitoExtension.class)
class RecordOrganizerTest {

  @Mock private AiClient aiClient;

  @InjectMocks private RecordOrganizer organizer;

  private static ProjectRecord record() {
    return ProjectRecord.builder()
        .project(Project.builder().userId(1L).name("사자록").build())
        .status(ProjectRecordStatus.DRAFT)
        .did("시안 2개 준비")
        .build();
  }

  @Test
  @DisplayName("AI 응답을 검증해 5칸 key 순서로 주고, 빈 문자열 칸은 제안 없음(null)으로 바꾼다")
  void organize_validResponse_returnsByKey() {
    when(aiClient.call(
            eq(RecordPrompts.ORGANIZE_SYSTEM), anyString(), eq(RecordOrganizeResult.class)))
        .thenReturn(new RecordOrganizeResult(" 시안 2개를 준비했다. ", null, "", null, null));

    Map<String, String> result = organizer.organize(record(), List.of(), List.of());

    assertThat(result.keySet()).containsExactly("did", "concern", "decision", "result", "nextStep");
    assertThat(result.get("did")).isEqualTo("시안 2개를 준비했다.");
    assertThat(result.get("decision")).isNull();
  }

  @Test
  @DisplayName("AiClient가 실패(AI_FAILED)·시간 초과(AI_TIMEOUT)를 던지면 그대로 올린다")
  void organize_aiError_propagates() {
    when(aiClient.call(anyString(), anyString(), eq(RecordOrganizeResult.class)))
        .thenThrow(new BusinessException(ErrorCode.AI_FAILED))
        .thenThrow(new BusinessException(ErrorCode.AI_TIMEOUT));

    assertThatThrownBy(() -> organizer.organize(record(), List.of(), List.of()))
        .extracting("errorCode")
        .isEqualTo(ErrorCode.AI_FAILED);
    assertThatThrownBy(() -> organizer.organize(record(), List.of(), List.of()))
        .extracting("errorCode")
        .isEqualTo(ErrorCode.AI_TIMEOUT);
  }
}
