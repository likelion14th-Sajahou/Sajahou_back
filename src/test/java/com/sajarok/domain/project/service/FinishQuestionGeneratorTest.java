package com.sajarok.domain.project.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.sajarok.domain.project.ai.FinishAttachments;
import com.sajarok.domain.project.ai.FinishPrompts;
import com.sajarok.domain.project.ai.FinishQuestionResult;
import com.sajarok.domain.project.ai.FinishQuestionResult.Question;
import com.sajarok.domain.project.entity.Project;
import com.sajarok.domain.project.entity.ProjectRecord;
import com.sajarok.domain.project.entity.ProjectRecordStatus;
import com.sajarok.domain.question.dto.NewQuestion;
import com.sajarok.global.exception.BusinessException;
import com.sajarok.global.exception.ErrorCode;
import com.sajarok.infra.ai.AiClient;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** 32번 FinishQuestionGenerator 테스트. 실제 OpenAI 대신 가짜 AiClient를 쓴다. */
@ExtendWith(MockitoExtension.class)
class FinishQuestionGeneratorTest {

  @Mock private AiClient aiClient;

  @InjectMocks private FinishQuestionGenerator generator;

  private static final Project PROJECT = Project.builder().userId(1L).name("사자록").build();

  private static List<ProjectRecord> records() {
    return List.of(
        ProjectRecord.builder()
            .project(PROJECT)
            .status(ProjectRecordStatus.SAVED)
            .did("API 연동")
            .build());
  }

  @Test
  @DisplayName("AI 응답이 검증을 통과하면 태그 순서(H-17)로 정렬해 빈 곳 이유와 함께 준다")
  void generate_validResponse_sortedByTag() {
    when(aiClient.call(
            eq(FinishPrompts.QUESTIONS_SYSTEM), anyString(), eq(FinishQuestionResult.class)))
        .thenReturn(
            new FinishQuestionResult(
                List.of(
                    new Question("배운 점", "프로젝트 전체를 돌아본 문장이 없어요", "다음 프로젝트에 꼭 가져갈 한 가지는요?"),
                    new Question("내 역할", "기록마다 역할이 흩어져 있어요", "내가 맡은 범위를 한 문장으로 말하면요?"))));

    List<NewQuestion> questions = generator.generate(PROJECT, records(), FinishAttachments.none());

    assertThat(questions).extracting(NewQuestion::tag).containsExactly("내 역할", "배운 점");
    assertThat(questions.get(0).quote()).isEqualTo("기록마다 역할이 흩어져 있어요");
  }

  @Test
  @DisplayName("AI가 같은 태그를 두 번 주면 AI_FAILED, AiClient의 AI_TIMEOUT은 그대로 올린다")
  void generate_invalidOrTimeout_throws() {
    Question dup = new Question("내 역할", "이유", "질문");
    when(aiClient.call(anyString(), anyString(), eq(FinishQuestionResult.class)))
        .thenReturn(new FinishQuestionResult(List.of(dup, dup)))
        .thenThrow(new BusinessException(ErrorCode.AI_TIMEOUT));

    assertThatThrownBy(() -> generator.generate(PROJECT, records(), FinishAttachments.none()))
        .extracting("errorCode")
        .isEqualTo(ErrorCode.AI_FAILED);
    assertThatThrownBy(() -> generator.generate(PROJECT, records(), FinishAttachments.none()))
        .extracting("errorCode")
        .isEqualTo(ErrorCode.AI_TIMEOUT);
  }
}
