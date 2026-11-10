package com.sajarok.domain.project.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.sajarok.domain.project.dto.PortfolioDraftResponse;
import com.sajarok.domain.project.entity.Project;
import com.sajarok.domain.project.entity.ProjectRecord;
import com.sajarok.domain.project.entity.ProjectRecordStatus;
import com.sajarok.domain.project.repository.ProjectRecordRepository;
import com.sajarok.domain.project.repository.ProjectRepository;
import com.sajarok.domain.question.dto.QuestionView;
import com.sajarok.domain.question.entity.QuestionOwner;
import com.sajarok.domain.question.entity.QuestionStatus;
import com.sajarok.domain.question.service.AiQuestionService;
import com.sajarok.global.exception.BusinessException;
import com.sajarok.global.exception.ErrorCode;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

/** 33번 포트폴리오 초안 서비스 테스트. AI(PortfolioDrafter)는 가짜, Markdown 조립은 진짜. */
@ExtendWith(MockitoExtension.class)
class ProjectFinishPortfolioDraftTest {

  @Mock private ProjectRepository projectRepository;
  @Mock private AiQuestionService aiQuestionService;
  @Mock private ProjectRecordRepository projectRecordRepository;
  @Mock private FinishQuestionGenerator finishQuestionGenerator;
  @Mock private PortfolioDrafter portfolioDrafter;

  @InjectMocks private ProjectFinishService projectFinishService;

  private static Project project() {
    Project project = Project.builder().userId(1L).name("사자록").build();
    ReflectionTestUtils.setField(project, "createdAt", LocalDateTime.of(2026, 11, 2, 10, 0));
    return project;
  }

  private static ProjectRecord record(Project project) {
    return ProjectRecord.builder()
        .project(project)
        .status(ProjectRecordStatus.SAVED)
        .did("API")
        .build();
  }

  private static QuestionView question(String tag, QuestionStatus status) {
    return new QuestionView(1L, 1, tag, "이유", null, "질문", "답", status);
  }

  private static Map<String, String> allBlank() {
    Map<String, String> bodies = new LinkedHashMap<>();
    List.of("role", "contribution", "problem", "result", "artifact", "learned")
        .forEach(key -> bodies.put(key, null));
    return bodies;
  }

  @Test
  @DisplayName("33번: 남의 프로젝트거나 없으면 NOT_FOUND, 기록·질문·AI를 부르지 않는다")
  void draft_notMine_throwsNotFound() {
    when(projectRepository.findByIdAndUserId(3L, 1L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> projectFinishService.createPortfolioDraft(1L, 3L, List.of(71L)))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.NOT_FOUND);
    verifyNoInteractions(projectRecordRepository, aiQuestionService, portfolioDrafter);
  }

  @Test
  @DisplayName("33번: 고른 기록 중 이 프로젝트의 SAVED 기록이 아닌 것이 있으면 NOT_FOUND, AI를 부르지 않는다")
  void draft_foreignRecord_throwsNotFound() {
    Project project = project();
    when(projectRepository.findByIdAndUserId(3L, 1L)).thenReturn(Optional.of(project));
    when(projectRecordRepository.findAllByProjectIdAndStatusAndIdInOrderByCreatedAtAscIdAsc(
            3L, ProjectRecordStatus.SAVED, Set.of(71L, 99L)))
        .thenReturn(List.of(record(project)));

    assertThatThrownBy(() -> projectFinishService.createPortfolioDraft(1L, 3L, List.of(71L, 99L)))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.NOT_FOUND);
    verifyNoInteractions(portfolioDrafter);
  }

  @Test
  @DisplayName("33번: 답한(ANSWERED) 마치기 질문만 AI에 넘기고(건너뛴·대기 중 제외), 결과를 Markdown으로 조립해 준다 (저장 안 함)")
  @SuppressWarnings("unchecked")
  void draft_passesOnlyAnsweredAndBuildsMarkdown() {
    Project project = project();
    List<ProjectRecord> records = List.of(record(project));
    when(projectRepository.findByIdAndUserId(3L, 1L)).thenReturn(Optional.of(project));
    when(projectRecordRepository.findAllByProjectIdAndStatusAndIdInOrderByCreatedAtAscIdAsc(
            3L, ProjectRecordStatus.SAVED, Set.of(71L)))
        .thenReturn(records);
    QuestionView answered = question("배운 점", QuestionStatus.ANSWERED);
    when(aiQuestionService.findAll(QuestionOwner.project(3L)))
        .thenReturn(
            List.of(
                question("내 역할", QuestionStatus.SKIPPED),
                answered,
                question("최종 결과", QuestionStatus.PENDING)));
    when(portfolioDrafter.draft(eq(project), eq(records), anyList())).thenReturn(allBlank());

    PortfolioDraftResponse response =
        projectFinishService.createPortfolioDraft(1L, 3L, List.of(71L, 71L));

    ArgumentCaptor<List<QuestionView>> captor = ArgumentCaptor.forClass(List.class);
    verify(portfolioDrafter).draft(eq(project), eq(records), captor.capture());
    assertThat(captor.getValue()).containsExactly(answered);
    assertThat(response.markdown()).startsWith("# 사자록\n");
    assertThat(response.blanks()).hasSize(6);
    verify(aiQuestionService, never()).replace(any(), anyList());
  }

  @ParameterizedTest
  @EnumSource(
      value = ErrorCode.class,
      names = {"AI_FAILED", "AI_TIMEOUT"})
  @DisplayName("33번: AI가 실패하거나 시간 초과면 같은 에러를 돌려준다")
  void draft_aiFails_rethrows(ErrorCode code) {
    Project project = project();
    when(projectRepository.findByIdAndUserId(3L, 1L)).thenReturn(Optional.of(project));
    when(projectRecordRepository.findAllByProjectIdAndStatusAndIdInOrderByCreatedAtAscIdAsc(
            eq(3L), eq(ProjectRecordStatus.SAVED), any()))
        .thenReturn(List.of(record(project)));
    when(aiQuestionService.findAll(QuestionOwner.project(3L))).thenReturn(List.of());
    when(portfolioDrafter.draft(any(), anyList(), anyList()))
        .thenThrow(new BusinessException(code));

    assertThatThrownBy(() -> projectFinishService.createPortfolioDraft(1L, 3L, List.of(71L)))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(code);
  }
}
