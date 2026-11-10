package com.sajarok.domain.project.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.sajarok.domain.project.dto.PortfolioSaveResponse;
import com.sajarok.domain.project.entity.Project;
import com.sajarok.domain.project.entity.ProjectRole;
import com.sajarok.domain.project.entity.ProjectStatus;
import com.sajarok.domain.project.repository.ProjectRepository;
import com.sajarok.domain.question.entity.QuestionOwner;
import com.sajarok.domain.question.service.AiQuestionService;
import com.sajarok.global.exception.BusinessException;
import com.sajarok.global.exception.ErrorCode;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import org.assertj.core.data.TemporalUnitOffset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ProjectFinishServiceTest {

  @Mock private ProjectRepository projectRepository;
  @Mock private AiQuestionService aiQuestionService;

  @InjectMocks private ProjectFinishService projectFinishService;

  private static Project project(ProjectStatus status, String portfolio) {
    Project project =
        Project.builder().userId(1L).name("사자록").roles(List.of(ProjectRole.BACKEND)).build();
    ReflectionTestUtils.setField(project, "status", status);
    ReflectionTestUtils.setField(project, "portfolioMarkdown", portfolio);
    return project;
  }

  @Test
  @DisplayName("34번 처음 저장: 진행 중이면 포트폴리오를 저장하고 DONE으로 바꾸며 isFirstCompletion=true, 시각은 +09:00")
  void savePortfolio_first_completesProject() {
    Project project = project(ProjectStatus.IN_PROGRESS, null);
    when(projectRepository.findByIdAndUserId(3L, 1L)).thenReturn(Optional.of(project));

    PortfolioSaveResponse response = projectFinishService.savePortfolio(1L, 3L, "# 사자록");

    assertThat(response.projectStatus()).isEqualTo(ProjectStatus.DONE);
    assertThat(response.isFirstCompletion()).isTrue();
    assertThat(response.portfolio().markdown()).isEqualTo("# 사자록");
    assertThat(response.portfolio().savedAt().getOffset()).isEqualTo(ZoneOffset.ofHours(9));
    assertThat(project.getPortfolioSavedAt())
        .isCloseTo(LocalDateTime.now(ZoneOffset.ofHours(9)), within5Seconds());
  }

  @Test
  @DisplayName("34번 다시 저장: 이미 완료면 포트폴리오만 덮어쓰고 isFirstCompletion=false")
  void savePortfolio_again_overwritesOnly() {
    Project project = project(ProjectStatus.DONE, "# 예전 포트폴리오");
    when(projectRepository.findByIdAndUserId(3L, 1L)).thenReturn(Optional.of(project));

    PortfolioSaveResponse response = projectFinishService.savePortfolio(1L, 3L, "# 새 포트폴리오");

    assertThat(response.isFirstCompletion()).isFalse();
    assertThat(response.projectStatus()).isEqualTo(ProjectStatus.DONE);
    assertThat(project.getPortfolioMarkdown()).isEqualTo("# 새 포트폴리오");
  }

  @Test
  @DisplayName("34번: 저장하면 이 프로젝트의 마치기 질문을 지운다")
  void savePortfolio_deletesFinishQuestions() {
    when(projectRepository.findByIdAndUserId(3L, 1L))
        .thenReturn(Optional.of(project(ProjectStatus.IN_PROGRESS, null)));

    projectFinishService.savePortfolio(1L, 3L, "# 사자록");

    verify(aiQuestionService).deleteAll(QuestionOwner.project(3L));
  }

  @Test
  @DisplayName("34번: 남의 프로젝트거나 없으면 NOT_FOUND이고 마치기 질문도 지우지 않는다")
  void savePortfolio_notMine_throwsNotFound() {
    when(projectRepository.findByIdAndUserId(3L, 1L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> projectFinishService.savePortfolio(1L, 3L, "# 사자록"))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.NOT_FOUND);
    verifyNoInteractions(aiQuestionService);
  }

  private static TemporalUnitOffset within5Seconds() {
    return within(5, ChronoUnit.SECONDS);
  }
}
