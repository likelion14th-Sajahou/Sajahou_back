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

import com.sajarok.domain.attachment.service.AttachmentService;
import com.sajarok.domain.project.ai.FinishAttachments;
import com.sajarok.domain.project.entity.Project;
import com.sajarok.domain.project.entity.ProjectRecord;
import com.sajarok.domain.project.entity.ProjectRecordStatus;
import com.sajarok.domain.project.entity.ProjectRole;
import com.sajarok.domain.project.repository.ProjectRecordRepository;
import com.sajarok.domain.project.repository.ProjectRepository;
import com.sajarok.domain.project.service.ProjectFinishService.FinishQuestions;
import com.sajarok.domain.question.dto.NewQuestion;
import com.sajarok.domain.question.dto.QuestionView;
import com.sajarok.domain.question.entity.QuestionOwner;
import com.sajarok.domain.question.entity.QuestionStatus;
import com.sajarok.domain.question.service.AiQuestionService;
import com.sajarok.global.exception.BusinessException;
import com.sajarok.global.exception.ErrorCode;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** 32번 마치기 질문 서비스 테스트. AI(FinishQuestionGenerator)와 질문 저장(AiQuestionService)은 가짜. */
@ExtendWith(MockitoExtension.class)
class ProjectFinishQuestionsTest {

  private static final QuestionOwner OWNER = QuestionOwner.project(3L);

  @Mock private ProjectRepository projectRepository;
  @Mock private AiQuestionService aiQuestionService;
  @Mock private ProjectRecordRepository projectRecordRepository;
  @Mock private FinishQuestionGenerator finishQuestionGenerator;
  @Mock private AttachmentService attachmentService;

  @InjectMocks private ProjectFinishService projectFinishService;

  private static Project project() {
    return Project.builder().userId(1L).name("사자록").roles(List.of(ProjectRole.BACKEND)).build();
  }

  private static ProjectRecord record(Project project) {
    return ProjectRecord.builder()
        .project(project)
        .status(ProjectRecordStatus.SAVED)
        .did("팀 API 연동을 맡았다")
        .build();
  }

  private static QuestionView view(String answer, QuestionStatus status) {
    return new QuestionView(
        980L, 4, "산출물 요청", "최종 산출물이 아직 첨부되지 않았어요", null, "산출물을 첨부해 주세요.", answer, status);
  }

  @Test
  @DisplayName("32번: 남의 프로젝트거나 없으면 NOT_FOUND, 질문·기록·AI를 부르지 않는다")
  void createQuestions_notMine_throwsNotFound() {
    when(projectRepository.findByIdAndUserId(3L, 1L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> projectFinishService.createQuestions(1L, 3L, List.of(71L)))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.NOT_FOUND);
    verifyNoInteractions(aiQuestionService, projectRecordRepository, finishQuestionGenerator);
  }

  @Test
  @DisplayName("32번: 진행 중인 질문이 있으면 AI를 부르지 않고 질문과 답을 그대로 돌려준다 (200)")
  void createQuestions_existing_returnsAsIsWithoutAi() {
    when(projectRepository.findByIdAndUserId(3L, 1L)).thenReturn(Optional.of(project()));
    when(aiQuestionService.findAll(OWNER))
        .thenReturn(List.of(view("발표 자료 링크예요", QuestionStatus.ANSWERED)));

    FinishQuestions result = projectFinishService.createQuestions(1L, 3L, List.of(71L));

    assertThat(result.created()).isFalse();
    assertThat(result.response().questions().get(0).answer()).isEqualTo("발표 자료 링크예요");
    assertThat(result.response().questions().get(0).status()).isEqualTo(QuestionStatus.ANSWERED);
    verifyNoInteractions(projectRecordRepository, finishQuestionGenerator);
    verify(aiQuestionService, never()).replace(any(), anyList());
  }

  @Test
  @DisplayName("32번: 고른 기록 중 이 프로젝트의 SAVED 기록이 아닌 것이 있으면 NOT_FOUND, AI를 부르지 않는다")
  void createQuestions_foreignRecord_throwsNotFound() {
    Project project = project();
    when(projectRepository.findByIdAndUserId(3L, 1L)).thenReturn(Optional.of(project));
    when(aiQuestionService.findAll(OWNER)).thenReturn(List.of());
    when(projectRecordRepository.findAllByProjectIdAndStatusAndIdInOrderByCreatedAtAscIdAsc(
            3L, ProjectRecordStatus.SAVED, Set.of(71L, 99L)))
        .thenReturn(List.of(record(project)));

    assertThatThrownBy(() -> projectFinishService.createQuestions(1L, 3L, List.of(71L, 99L)))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.NOT_FOUND);
    verifyNoInteractions(finishQuestionGenerator);
  }

  @Test
  @DisplayName("32번: 질문이 없으면 고른 기록(중복은 합침)으로 AI 질문을 만들어 프로젝트 소속으로 저장한다 (201)")
  void createQuestions_new_generatesAndSaves() {
    Project project = project();
    List<ProjectRecord> records = List.of(record(project), record(project));
    when(projectRepository.findByIdAndUserId(3L, 1L)).thenReturn(Optional.of(project));
    when(aiQuestionService.findAll(OWNER)).thenReturn(List.of());
    when(projectRecordRepository.findAllByProjectIdAndStatusAndIdInOrderByCreatedAtAscIdAsc(
            3L, ProjectRecordStatus.SAVED, Set.of(71L, 74L)))
        .thenReturn(records);
    List<NewQuestion> questions =
        List.of(new NewQuestion("산출물 요청", "최종 산출물이 아직 첨부되지 않았어요", null, "산출물을 첨부해 주세요."));
    when(finishQuestionGenerator.generate(project, records, FinishAttachments.none()))
        .thenReturn(questions);
    when(aiQuestionService.replace(OWNER, questions))
        .thenReturn(List.of(view(null, QuestionStatus.PENDING)));

    FinishQuestions result = projectFinishService.createQuestions(1L, 3L, List.of(71L, 74L, 71L));

    assertThat(result.created()).isTrue();
    assertThat(result.response().questions()).hasSize(1);
    assertThat(result.response().questions().get(0).quote()).isEqualTo("최종 산출물이 아직 첨부되지 않았어요");
    assertThat(result.response().questions().get(0).answer()).isNull();
  }

  @ParameterizedTest
  @EnumSource(
      value = ErrorCode.class,
      names = {"AI_FAILED", "AI_TIMEOUT"})
  @DisplayName("32번: AI가 실패하거나 시간 초과면 같은 에러를 돌려주고 질문을 저장하지 않는다")
  void createQuestions_aiFails_savesNothing(ErrorCode code) {
    Project project = project();
    when(projectRepository.findByIdAndUserId(3L, 1L)).thenReturn(Optional.of(project));
    when(aiQuestionService.findAll(OWNER)).thenReturn(List.of());
    when(projectRecordRepository.findAllByProjectIdAndStatusAndIdInOrderByCreatedAtAscIdAsc(
            eq(3L), eq(ProjectRecordStatus.SAVED), any()))
        .thenReturn(List.of(record(project)));
    when(finishQuestionGenerator.generate(eq(project), anyList(), any()))
        .thenThrow(new BusinessException(code));

    assertThatThrownBy(() -> projectFinishService.createQuestions(1L, 3L, List.of(71L)))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(code);
    verify(aiQuestionService, never()).replace(any(), anyList());
  }
}
