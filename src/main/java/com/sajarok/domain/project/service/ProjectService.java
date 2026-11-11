package com.sajarok.domain.project.service;

import com.sajarok.domain.attachment.dto.AttachmentOwner;
import com.sajarok.domain.attachment.dto.AttachmentView;
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
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProjectService {

  private final ProjectRepository projectRepository;
  private final ProjectRecordRepository projectRecordRepository;
  private final StoryCardService storyCardService;
  private final ProjectRecordService projectRecordService;
  private final AiQuestionService aiQuestionService;
  private final AttachmentService attachmentService;

  /** 25번: 프로젝트를 만든다. 같은 역할을 여러 번 보내면 하나로 줄인다. 상태는 진행 중(IN_PROGRESS)으로 시작한다. */
  @Transactional
  public ProjectCreateResponse create(Long userId, ProjectCreateRequest request) {
    Project project =
        Project.builder()
            .userId(userId)
            .name(request.name())
            .subtitle(request.subtitle())
            .roles(request.roles().stream().distinct().toList())
            .build();
    return ProjectCreateResponse.from(projectRepository.save(project));
  }

  /** 43번: 내 프로젝트 목록. 진행 중·완료로 나누고, 각각 만든 순서 최신순. */
  public ProjectListResponse getMyProjects(Long userId) {
    List<Project> projects = projectRepository.findAllByUserIdOrderByCreatedAtDescIdDesc(userId);
    Map<Long, RecordStats> stats = loadSavedStats(projects);
    Function<Project, ProjectSummary> toSummary =
        p ->
            new ProjectSummary(
                p.getId(),
                p.getName(),
                p.getSubtitle(),
                List.copyOf(p.getRoles()),
                recordCount(stats, p),
                lastRecordDate(stats, p));
    return new ProjectListResponse(
        filter(projects, ProjectStatus.IN_PROGRESS).stream().map(toSummary).toList(),
        filter(projects, ProjectStatus.DONE).stream().map(toSummary).toList());
  }

  /**
   * 26번: 프로젝트 상세. 남의 프로젝트거나 없으면 NOT_FOUND.
   *
   * <p>저장된(SAVED) 기록을 쿼리 한 번으로 읽고, 기록 수·마지막 날짜는 그 목록에서 계산한다(통계 쿼리를 따로 부르지 않음).
   */
  public ProjectDetailResponse getDetail(Long userId, Long projectId) {
    Project project =
        projectRepository
            .findByIdAndUserId(projectId, userId)
            .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
    List<ProjectRecord> records =
        projectRecordRepository.findAllByProjectIdAndStatusOrderByCreatedAtDescIdDesc(
            projectId, ProjectRecordStatus.SAVED);

    // 카드 ID·첨부를 기록마다 따로 묻지 않고 한 번에 가져온다 (N+1 방지).
    Map<Long, Long> cardIds = Map.of();
    Map<Long, List<AttachmentView>> attachments = Map.of();
    if (!records.isEmpty()) {
      List<Long> recordIds = records.stream().map(ProjectRecord::getId).toList();
      attachments = attachmentService.findAll(AttachmentOwner.Kind.PROJECT_RECORD, recordIds);
      cardIds =
          storyCardService.findByRecordIds(recordIds).values().stream()
              .filter(card -> card.cardId() != null)
              .collect(Collectors.toMap(RecordCardInfo::recordId, RecordCardInfo::cardId));
    }
    return ProjectDetailResponse.of(project, records, cardIds, attachments);
  }

  /**
   * 44번: 프로젝트 삭제. 기록(DRAFT 포함)·프로젝트 카드·마치기 질문·포트폴리오·첨부를 모두 지운다 (H-25). 남의 프로젝트거나 없으면 NOT_FOUND.
   *
   * <p>한 트랜잭션에서 자식부터 지운다(FK에 막히지 않게): 기록별 카드·AI 질문 → 마치기 질문 → 기록 행 → 프로젝트. 카드 삭제는 인터페이스에 기록 단위 메서드만
   * 있어서 기록마다 부른다.
   */
  @Transactional
  public void delete(Long userId, Long projectId) {
    Project project =
        projectRepository
            .findByIdAndUserId(projectId, userId)
            .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));

    projectRecordRepository
        .findIdsByProjectId(projectId)
        .forEach(projectRecordService::deleteChildren);
    aiQuestionService.deleteAll(QuestionOwner.project(projectId));
    attachmentService.deleteAll(AttachmentOwner.project(projectId));

    projectRecordRepository.deleteAllByProjectId(projectId);
    projectRepository.delete(project);
  }

  /** 홈(4번)용 프로젝트 타일. 홈 담당이 부른다. */
  public HomeProjectTiles homeTiles(Long userId) {
    List<Project> projects = projectRepository.findAllByUserIdOrderByCreatedAtDescIdDesc(userId);
    Map<Long, RecordStats> stats = loadSavedStats(projects);
    return new HomeProjectTiles(
        filter(projects, ProjectStatus.IN_PROGRESS).stream()
            .map(
                p ->
                    new InProgressTile(
                        p.getId(), p.getName(), recordCount(stats, p), lastRecordDate(stats, p)))
            .toList(),
        filter(projects, ProjectStatus.DONE).stream()
            .map(p -> new DoneTile(p.getId(), p.getName(), recordCount(stats, p)))
            .toList());
  }

  /** 프로젝트 주인 회원 ID. 다른 도메인이 소유 확인에 쓴다. 없으면 NOT_FOUND. */
  public Long getOwnerId(Long projectId) {
    return projectRepository
        .findUserIdById(projectId)
        .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
  }

  /** 프로젝트 ID → 이름. 없는 ID는 결과에서 빠진다. */
  public Map<Long, String> names(Collection<Long> projectIds) {
    if (projectIds == null || projectIds.isEmpty()) {
      return Map.of();
    }
    return projectRepository.findNamesByIdIn(projectIds).stream()
        .collect(Collectors.toMap(ProjectName::projectId, ProjectName::name));
  }

  /** 프로젝트들의 저장된 기록 통계를 쿼리 한 번으로 가져온다. */
  private Map<Long, RecordStats> loadSavedStats(List<Project> projects) {
    if (projects.isEmpty()) {
      return Map.of();
    }
    List<Long> ids = projects.stream().map(Project::getId).toList();
    return projectRecordRepository.findStats(ids, ProjectRecordStatus.SAVED).stream()
        .collect(Collectors.toMap(RecordStats::projectId, Function.identity()));
  }

  private static List<Project> filter(List<Project> projects, ProjectStatus status) {
    return projects.stream().filter(p -> p.getStatus() == status).toList();
  }

  private static long recordCount(Map<Long, RecordStats> stats, Project project) {
    RecordStats s = stats.get(project.getId());
    return s == null ? 0 : s.savedCount();
  }

  private static LocalDate lastRecordDate(Map<Long, RecordStats> stats, Project project) {
    RecordStats s = stats.get(project.getId());
    return s == null ? null : s.lastCreatedAt().toLocalDate();
  }
}
