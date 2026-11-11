package com.sajarok.domain.project.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.sajarok.domain.project.ai.FinishAttachments;
import com.sajarok.domain.project.entity.Project;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 33번 PortfolioDrafter 테스트 (AiClient 연결 전). */
class PortfolioDrafterTest {

  @Test
  @DisplayName("AiClient 연결 전: 6개 항목 모두 빈칸(null)인 결과가 검증을 통과해 key 순서대로 나온다")
  void draft_beforeAiClient_allBlank() {
    Map<String, String> bodies =
        new PortfolioDrafter()
            .draft(
                Project.builder().userId(1L).name("사자록").build(),
                List.of(),
                List.of(),
                FinishAttachments.none());

    assertThat(bodies.keySet())
        .containsExactly("role", "contribution", "problem", "result", "artifact", "learned");
    assertThat(bodies.values()).containsOnlyNulls();
  }
}
