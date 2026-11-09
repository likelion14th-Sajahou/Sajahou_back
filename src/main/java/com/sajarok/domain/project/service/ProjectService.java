package com.sajarok.domain.project.service;

import com.sajarok.domain.project.dto.HomeProjectTiles;
import com.sajarok.domain.project.dto.HomeProjectTiles.DoneTile;
import com.sajarok.domain.project.dto.HomeProjectTiles.InProgressTile;
import com.sajarok.domain.project.dto.ProjectCreateRequest;
import com.sajarok.domain.project.dto.ProjectCreateResponse;
import com.sajarok.domain.project.dto.ProjectListResponse;
import com.sajarok.domain.project.dto.ProjectListResponse.ProjectSummary;
import com.sajarok.domain.project.entity.Project;
import com.sajarok.domain.project.entity.ProjectRecordStatus;
import com.sajarok.domain.project.entity.ProjectStatus;
import com.sajarok.domain.project.repository.ProjectRecordRepository;
import com.sajarok.domain.project.repository.ProjectRepository;
import com.sajarok.domain.project.repository.RecordStats;
import java.time.LocalDate;
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
