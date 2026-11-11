package com.sajarok.domain.project.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.groups.Tuple.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.sajarok.domain.attachment.dto.AttachmentOwner;
import com.sajarok.domain.attachment.dto.AttachmentView;
import com.sajarok.domain.attachment.entity.AttachmentCategory;
import com.sajarok.domain.attachment.entity.AttachmentType;
import com.sajarok.domain.attachment.entity.ExtractStatus;
import com.sajarok.domain.attachment.service.AttachmentService;
import com.sajarok.domain.project.dto.HomeProjectTiles;
import com.sajarok.domain.project.dto.HomeProjectTiles.DoneTile;
import com.sajarok.domain.project.dto.HomeProjectTiles.InProgressTile;
import com.sajarok.domain.project.dto.ProjectCreateRequest;
import com.sajarok.domain.project.dto.ProjectCreateResponse;
import com.sajarok.domain.project.dto.ProjectDetailResponse;
import com.sajarok.domain.project.dto.ProjectListResponse;
import com.sajarok.domain.project.dto.ProjectListResponse.ProjectSummary;
import com.sajarok.domain.project.entity.Project;
import com.sajarok.domain.project.entity.ProjectRecord;
import com.sajarok.domain.project.entity.ProjectRecordStatus;
import com.sajarok.domain.project.entity.ProjectRole;
import com.sajarok.domain.project.entity.ProjectStatus;
import com.sajarok.domain.project.repository.ProjectName;
import com.sajarok.domain.project.repository.ProjectRecordRepository;
import com.sajarok.domain.project.repository.ProjectRepository;
import com.sajarok.domain.project.repository.RecordStats;
import com.sajarok.domain.question.entity.QuestionOwner;
import com.sajarok.domain.question.service.AiQuestionService;
import com.sajarok.domain.storycard.dto.RecordCardInfo;
import com.sajarok.domain.storycard.service.StoryCardService;
import com.sajarok.global.exception.BusinessException;
import com.sajarok.global.exception.ErrorCode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ProjectServiceTest {

  @Mock private ProjectRepository projectRepository;
  @Mock private ProjectRecordRepository projectRecordRepository;
  @Mock private StoryCardService storyCardService;
  @Mock private ProjectRecordService projectRecordService;
  @Mock private AiQuestionService aiQuestionService;
  @Mock private AttachmentService attachmentService;

  @InjectMocks private ProjectService projectService;

  @Test
  @DisplayName("프로젝트를 만들면 로그인 회원 것으로, 진행 중 상태로 저장되고 중복 역할은 하나로 줄어든다")
  void create_savesInProgressProjectWithDistinctRoles() {
    when(projectRepository.save(any(Project.class))).thenAnswer(inv -> inv.getArgument(0));
    ProjectCreateRequest request =
        new ProjectCreateRequest(
            "사자록", "멋사 회고 서비스", List.of(ProjectRole.PM, ProjectRole.BACKEND, ProjectRole.PM));

    ProjectCreateResponse response = projectService.create(1L, request);

    ArgumentCaptor<Project> saved = ArgumentCaptor.forClass(Project.class);
    verify(projectRepository).save(saved.capture());
    assertThat(saved.getValue().getUserId()).isEqualTo(1L);
    assertThat(saved.getValue().getStatus()).isEqualTo(ProjectStatus.IN_PROGRESS);
    assertThat(saved.getValue().getRoles()).containsExactly(ProjectRole.PM, ProjectRole.BACKEND);
    assertThat(response.name()).isEqualTo("사자록");
    assertThat(response.subtitle()).isEqualTo("멋사 회고 서비스");
    assertThat(response.roles()).containsExactly(ProjectRole.PM, ProjectRole.BACKEND);
    assertThat(response.status()).isEqualTo(ProjectStatus.IN_PROGRESS);
  }

  @Test
  @DisplayName("목록은 진행 중·완료로 나뉘고, 저장된 기록 수와 마지막 기록 날짜가 붙으며, 기록이 없으면 0과 null이다")
  void getMyProjects_splitsByStatusAndFillsRecordStats() {
    Project inProgress = project(3L, "사자록", ProjectStatus.IN_PROGRESS);
    Project done = project(1L, "아이디어톤", ProjectStatus.DONE);
    when(projectRepository.findAllByUserIdOrderByCreatedAtDescIdDesc(1L))
        .thenReturn(List.of(inProgress, done));
    when(projectRecordRepository.findStats(List.of(3L, 1L), ProjectRecordStatus.SAVED))
        .thenReturn(List.of(new RecordStats(3L, 5L, LocalDateTime.of(2026, 11, 4, 21, 30))));

    ProjectListResponse response = projectService.getMyProjects(1L);

    assertThat(response.inProgressProjects())
        .containsExactly(
            new ProjectSummary(
                3L, "사자록", null, List.of(ProjectRole.BACKEND), 5, LocalDate.of(2026, 11, 4)));
    assertThat(response.doneProjects())
        .containsExactly(
            new ProjectSummary(1L, "아이디어톤", null, List.of(ProjectRole.BACKEND), 0, null));
  }

  @Test
  @DisplayName("프로젝트가 하나도 없으면 빈 목록을 주고 기록 수 쿼리는 부르지 않는다")
  void getMyProjects_noProjects_returnsEmptyListsWithoutStatsQuery() {
    when(projectRepository.findAllByUserIdOrderByCreatedAtDescIdDesc(1L)).thenReturn(List.of());

    ProjectListResponse response = projectService.getMyProjects(1L);

    assertThat(response.inProgressProjects()).isEmpty();
    assertThat(response.doneProjects()).isEmpty();
    verify(projectRecordRepository, never()).findStats(anyCollection(), any());
  }

  @Test
  @DisplayName("홈 타일은 4번 형태대로 진행 중에는 마지막 기록 날짜를, 완료에는 기록 수만 넣는다")
  void homeTiles_matchesHomeSpecShape() {
    Project inProgress = project(3L, "사자록", ProjectStatus.IN_PROGRESS);
    Project done = project(1L, "아이디어톤", ProjectStatus.DONE);
    when(projectRepository.findAllByUserIdOrderByCreatedAtDescIdDesc(1L))
        .thenReturn(List.of(inProgress, done));
    when(projectRecordRepository.findStats(List.of(3L, 1L), ProjectRecordStatus.SAVED))
        .thenReturn(
            List.of(
                new RecordStats(3L, 5L, LocalDateTime.of(2026, 11, 4, 10, 0)),
                new RecordStats(1L, 8L, LocalDateTime.of(2026, 5, 26, 10, 0))));

    HomeProjectTiles tiles = projectService.homeTiles(1L);

    assertThat(tiles.inProgressProjects())
        .containsExactly(new InProgressTile(3L, "사자록", 5, LocalDate.of(2026, 11, 4)));
    assertThat(tiles.doneProjects()).containsExactly(new DoneTile(1L, "아이디어톤", 8));
  }

  @Test
  @DisplayName("getOwnerId: 프로젝트가 있으면 주인 회원 ID를, 없으면 NOT_FOUND를 준다")
  void getOwnerId_returnsOwnerOrThrowsNotFound() {
    when(projectRepository.findUserIdById(3L)).thenReturn(Optional.of(1L));
    when(projectRepository.findUserIdById(999L)).thenReturn(Optional.empty());

    assertThat(projectService.getOwnerId(3L)).isEqualTo(1L);
    assertThatThrownBy(() -> projectService.getOwnerId(999L))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.NOT_FOUND);
  }

  @Test
  @DisplayName("names: 프로젝트 ID → 이름 Map을 주고, 빈 목록이면 쿼리 없이 빈 Map을 준다")
  void names_returnsIdToNameMap() {
    when(projectRepository.findNamesByIdIn(List.of(3L, 1L)))
        .thenReturn(List.of(new ProjectName(3L, "사자록"), new ProjectName(1L, "아이디어톤")));

    assertThat(projectService.names(List.of(3L, 1L))).isEqualTo(Map.of(3L, "사자록", 1L, "아이디어톤"));
    assertThat(projectService.names(List.of())).isEmpty();
    verify(projectRepository).findNamesByIdIn(anyCollection());
  }

  /** 테스트용 프로젝트. id와 상태는 DB나 엔티티 메서드가 정하므로 테스트에서는 직접 넣는다. */
  private static Project project(Long id, String name, ProjectStatus status) {
    Project project =
        Project.builder().userId(1L).name(name).roles(List.of(ProjectRole.BACKEND)).build();
    ReflectionTestUtils.setField(project, "id", id);
    ReflectionTestUtils.setField(project, "status", status);
    return project;
  }

  // ---- 26번 프로젝트 상세 ----

  private Project detailProject(String portfolioMarkdown, LocalDateTime savedAt) {
    Project project =
        Project.builder()
            .userId(1L)
            .name("사자록")
            .subtitle("멋사 회고 서비스")
            .roles(List.of(ProjectRole.BACKEND))
            .build();
    ReflectionTestUtils.setField(project, "id", 3L);
    ReflectionTestUtils.setField(project, "createdAt", LocalDateTime.of(2026, 10, 20, 10, 0));
    ReflectionTestUtils.setField(project, "portfolioMarkdown", portfolioMarkdown);
    ReflectionTestUtils.setField(project, "portfolioSavedAt", savedAt);
    return project;
  }

  private ProjectRecord savedRecord(Project project, long id, LocalDateTime createdAt) {
    ProjectRecord record =
        ProjectRecord.builder()
            .project(project)
            .status(ProjectRecordStatus.SAVED)
            .did("한 일 " + id)
            .build();
    ReflectionTestUtils.setField(record, "id", id);
    ReflectionTestUtils.setField(record, "createdAt", createdAt);
    return record;
  }

  @Test
  @DisplayName("26번: SAVED 기록만 최신순으로 주고, 기록 수·마지막 날짜를 그 목록에서 계산한다")
  void getDetail_returnsSavedRecordsAndStats() {
    Project project = detailProject(null, null);
    when(projectRepository.findByIdAndUserId(3L, 1L)).thenReturn(Optional.of(project));
    when(projectRecordRepository.findAllByProjectIdAndStatusOrderByCreatedAtDescIdDesc(
            3L, ProjectRecordStatus.SAVED))
        .thenReturn(
            List.of(
                savedRecord(project, 78L, LocalDateTime.of(2026, 11, 4, 9, 0)),
                savedRecord(project, 77L, LocalDateTime.of(2026, 11, 1, 9, 0))));

    ProjectDetailResponse response = projectService.getDetail(1L, 3L);

    assertThat(response.projectId()).isEqualTo(3L);
    assertThat(response.startedOn()).isEqualTo(LocalDate.of(2026, 10, 20));
    assertThat(response.recordCount()).isEqualTo(2);
    assertThat(response.lastRecordDate()).isEqualTo(LocalDate.of(2026, 11, 4));
    assertThat(response.records())
        .extracting(ProjectDetailResponse.RecordItem::recordId)
        .containsExactly(78L, 77L);
    assertThat(response.portfolio()).isNull();
  }

  @Test
  @DisplayName("26번: 기록이 없으면 기록 수 0·마지막 날짜 null, 포트폴리오가 있으면 +09:00 시각으로 준다")
  void getDetail_noRecords_withPortfolio() {
    Project project = detailProject("# 사자록", LocalDateTime.of(2026, 11, 18, 21, 0));
    when(projectRepository.findByIdAndUserId(3L, 1L)).thenReturn(Optional.of(project));
    when(projectRecordRepository.findAllByProjectIdAndStatusOrderByCreatedAtDescIdDesc(
            3L, ProjectRecordStatus.SAVED))
        .thenReturn(List.of());

    ProjectDetailResponse response = projectService.getDetail(1L, 3L);

    assertThat(response.recordCount()).isZero();
    assertThat(response.lastRecordDate()).isNull();
    assertThat(response.portfolio().markdown()).isEqualTo("# 사자록");
    assertThat(response.portfolio().savedAt())
        .isEqualTo(OffsetDateTime.parse("2026-11-18T21:00:00+09:00"));
  }

  @Test
  @DisplayName("26번: 남의 프로젝트거나 없으면 NOT_FOUND")
  void getDetail_notMine_throwsNotFound() {
    when(projectRepository.findByIdAndUserId(3L, 1L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> projectService.getDetail(1L, 3L))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.NOT_FOUND);
  }

  @Test
  @DisplayName("26번: 카드 ID·첨부를 기록 ID 전체로 한 번씩만 묻고(N+1 방지), 기록마다 맞는 cardId·첨부를 넣는다 (첨부 없으면 빈 배열)")
  void getDetail_fetchesCardIdsOnce() {
    Project project = detailProject(null, null);
    when(projectRepository.findByIdAndUserId(3L, 1L)).thenReturn(Optional.of(project));
    when(projectRecordRepository.findAllByProjectIdAndStatusOrderByCreatedAtDescIdDesc(
            3L, ProjectRecordStatus.SAVED))
        .thenReturn(
            List.of(
                savedRecord(project, 78L, LocalDateTime.of(2026, 11, 4, 9, 0)),
                savedRecord(project, 77L, LocalDateTime.of(2026, 11, 1, 9, 0))));
    when(storyCardService.findByRecordIds(List.of(78L, 77L)))
        .thenReturn(
            Map.of(
                78L, new RecordCardInfo(78L, 413L, Set.of()),
                77L, new RecordCardInfo(77L, 412L, Set.of())));
    AttachmentView meetingNote =
        new AttachmentView(
            601L,
            AttachmentType.FILE,
            AttachmentCategory.MEETING_NOTE,
            "회의록.pdf",
            null,
            null,
            "application/pdf",
            10L,
            ExtractStatus.DONE);
    when(attachmentService.findAll(AttachmentOwner.Kind.PROJECT_RECORD, List.of(78L, 77L)))
        .thenReturn(Map.of(78L, List.of(meetingNote)));

    ProjectDetailResponse response = projectService.getDetail(1L, 3L);

    verify(storyCardService).findByRecordIds(List.of(78L, 77L));
    assertThat(response.records())
        .extracting(
            ProjectDetailResponse.RecordItem::recordId, ProjectDetailResponse.RecordItem::cardId)
        .containsExactly(tuple(78L, 413L), tuple(77L, 412L));
    assertThat(response.records().get(0).attachments()).containsExactly(meetingNote);
    assertThat(response.records().get(1).attachments()).isEmpty();
  }

  // ---- 44번 프로젝트 삭제 ----

  @Test
  @DisplayName("44번: 기록마다(DRAFT 포함) 딸린 것 정리 → 마치기 질문 → 기록 행 → 프로젝트 순서로 지운다")
  void delete_deletesChildrenFirst() {
    Project project = detailProject(null, null);
    when(projectRepository.findByIdAndUserId(3L, 1L)).thenReturn(Optional.of(project));
    when(projectRecordRepository.findIdsByProjectId(3L)).thenReturn(List.of(77L, 78L));

    projectService.delete(1L, 3L);

    InOrder order =
        inOrder(
            projectRecordService,
            aiQuestionService,
            attachmentService,
            projectRecordRepository,
            projectRepository);
    order.verify(projectRecordService).deleteChildren(77L);
    order.verify(projectRecordService).deleteChildren(78L);
    order.verify(aiQuestionService).deleteAll(QuestionOwner.project(3L));
    order.verify(attachmentService).deleteAll(AttachmentOwner.project(3L));
    order.verify(projectRecordRepository).deleteAllByProjectId(3L);
    order.verify(projectRepository).delete(project);
  }

  @Test
  @DisplayName("44번: 기록이 없는 프로젝트도 마치기 질문과 프로젝트를 지운다")
  void delete_noRecords_stillDeletesProject() {
    Project project = detailProject(null, null);
    when(projectRepository.findByIdAndUserId(3L, 1L)).thenReturn(Optional.of(project));
    when(projectRecordRepository.findIdsByProjectId(3L)).thenReturn(List.of());

    projectService.delete(1L, 3L);

    verifyNoInteractions(projectRecordService);
    verify(aiQuestionService).deleteAll(QuestionOwner.project(3L));
    verify(projectRepository).delete(project);
  }

  @Test
  @DisplayName("44번: 남의 프로젝트거나 없으면 NOT_FOUND이고 아무것도 지우지 않는다")
  void delete_notMine_deletesNothing() {
    when(projectRepository.findByIdAndUserId(3L, 1L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> projectService.delete(1L, 3L))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.NOT_FOUND);
    verifyNoInteractions(projectRecordService, aiQuestionService);
    verify(projectRecordRepository, never()).deleteAllByProjectId(anyLong());
    verify(projectRepository, never()).delete(any());
  }
}
