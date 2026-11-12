package com.sajarok.domain.project.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.sajarok.domain.project.ai.RecordAttachment;
import com.sajarok.domain.project.ai.RecordPrompts;
import com.sajarok.domain.project.ai.RecordQuestionResult;
import com.sajarok.domain.project.ai.RecordQuestionResult.Question;
import com.sajarok.domain.project.ai.RecordQuestionResult.Validated;
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

/** 30번 RecordQuestionGenerator 테스트. 실제 OpenAI 대신 가짜 AiClient를 쓴다. */
@ExtendWith(MockitoExtension.class)
class RecordQuestionGeneratorTest {

  @Mock private AiClient aiClient;

  @InjectMocks private RecordQuestionGenerator generator;

  private static final RecordAttachment DESIGN =
      new RecordAttachment(602L, "FILE", "DESIGN", "홈 시안.png", "시안 A 회고 마감 강조, 시안 B 프로젝트 함께 보기");

  private static ProjectRecord record() {
    return ProjectRecord.builder()
        .project(Project.builder().userId(1L).name("사자록").build())
        .status(ProjectRecordStatus.DRAFT)
        .did("기획 회의에서 홈 화면 시안 2개를 준비했다")
        .build();
  }

  private void aiReturns(RecordQuestionResult result) {
    when(aiClient.call(
            eq(RecordPrompts.QUESTIONS_SYSTEM), anyString(), eq(RecordQuestionResult.class)))
        .thenReturn(result);
  }

  @Test
  @DisplayName("AI 응답이 검증을 통과하면 질문으로 쓰고, 원문에 없는 quote는 null로 바꾼다")
  void generate_validResponse_returnsCheckedQuestions() {
    aiReturns(
        new RecordQuestionResult(
            false,
            List.of(
                new Question(
                    "선택의 이유",
                    "시안 A 회고 마감 강조, 시안 B 프로젝트 함께 보기",
                    602L,
                    "두 시안 중 어느 쪽으로 정했고, 왜 그렇게 정했어요?"),
                new Question("근거와 숫자", "클릭률 30% 상승", null, "결과나 반응이 있었나요?"))));

    Validated v = generator.generate(record(), List.of(DESIGN));

    assertThat(v.insufficientMaterial()).isFalse();
    assertThat(v.questions())
        .extracting(NewQuestion::quote, NewQuestion::sourceAttachmentId)
        .containsExactly(tuple("시안 A 회고 마감 강조, 시안 B 프로젝트 함께 보기", 602L), tuple(null, null));
  }

  @Test
  @DisplayName("AI가 목록 밖 태그를 주면 AI_FAILED")
  void generate_unknownTag_throwsAiFailed() {
    aiReturns(
        new RecordQuestionResult(false, List.of(new Question("배운 점", null, null, "무엇을 배웠나요?"))));

    assertThatThrownBy(() -> generator.generate(record(), List.of()))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.AI_FAILED);
  }

  @Test
  @DisplayName("AiClient가 시간 초과(AI_TIMEOUT)를 던지면 그대로 올린다")
  void generate_timeout_propagates() {
    when(aiClient.call(anyString(), anyString(), eq(RecordQuestionResult.class)))
        .thenThrow(new BusinessException(ErrorCode.AI_TIMEOUT));

    assertThatThrownBy(() -> generator.generate(record(), List.of()))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.AI_TIMEOUT);
  }
}
