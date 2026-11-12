package com.sajarok.domain.project.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.sajarok.domain.project.ai.FinishAttachments;
import com.sajarok.domain.project.ai.FinishPrompts;
import com.sajarok.domain.project.ai.PortfolioDraftResult;
import com.sajarok.domain.project.ai.PortfolioDraftResult.Section;
import com.sajarok.domain.project.entity.Project;
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

/** 33번 PortfolioDrafter 테스트. 실제 OpenAI 대신 가짜 AiClient를 쓴다. */
@ExtendWith(MockitoExtension.class)
class PortfolioDrafterTest {

  @Mock private AiClient aiClient;

  @InjectMocks private PortfolioDrafter drafter;

  private static final Project PROJECT = Project.builder().userId(1L).name("사자록").build();

  private Map<String, String> draft() {
    return drafter.draft(PROJECT, List.of(), List.of(), FinishAttachments.none());
  }

  @Test
  @DisplayName("AI가 순서를 섞어 줘도 6개 항목 key 순서로 정리하고, 빈 본문은 빈칸(null)으로 둔다")
  void draft_validResponse_orderedByKey() {
    List<Section> shuffled =
        List.of(
            new Section("learned", "배운 점"),
            new Section("role", "백엔드 API 담당"),
            new Section("contribution", null),
            new Section("problem", " "),
            new Section("result", null),
            new Section("artifact", null));
    when(aiClient.call(
            eq(FinishPrompts.PORTFOLIO_DRAFT_SYSTEM), anyString(), eq(PortfolioDraftResult.class)))
        .thenReturn(new PortfolioDraftResult(shuffled));

    Map<String, String> bodies = draft();

    assertThat(bodies.keySet())
        .containsExactly("role", "contribution", "problem", "result", "artifact", "learned");
    assertThat(bodies.get("role")).isEqualTo("백엔드 API 담당");
    assertThat(bodies.get("problem")).isNull();
  }

  @Test
  @DisplayName("AI가 항목을 빠뜨리면 AI_FAILED, AiClient의 AI_TIMEOUT은 그대로 올린다")
  void draft_invalidOrTimeout_throws() {
    when(aiClient.call(anyString(), anyString(), eq(PortfolioDraftResult.class)))
        .thenReturn(new PortfolioDraftResult(List.of(new Section("role", "백엔드"))))
        .thenThrow(new BusinessException(ErrorCode.AI_TIMEOUT));

    assertThatThrownBy(this::draft).extracting("errorCode").isEqualTo(ErrorCode.AI_FAILED);
    assertThatThrownBy(this::draft).extracting("errorCode").isEqualTo(ErrorCode.AI_TIMEOUT);
  }
}
