package com.sajarok.domain.project.service;

import com.sajarok.domain.project.dto.ProjectRecordCreateRequest;
import com.sajarok.domain.project.dto.ProjectRecordResponse;
import com.sajarok.domain.project.entity.Project;
import com.sajarok.domain.project.entity.ProjectRecord;
import com.sajarok.domain.project.entity.ProjectRecordStatus;
import com.sajarok.domain.project.repository.ProjectRecordRepository;
import com.sajarok.domain.project.repository.ProjectRepository;
import com.sajarok.domain.project.repository.RecordDate;
import com.sajarok.global.exception.BusinessException;
import com.sajarok.global.exception.ErrorCode;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProjectRecordService {

  private final ProjectRecordRepository projectRecordRepository;
  private final ProjectRepository projectRepository;

  /**
   * 27번: 기록 추가. 마친(DONE) 프로젝트에도 추가할 수 있다 (H-11·H-21: 완료해도 잠기지 않음).
   *
   * <p>남의 프로젝트거나 없으면 NOT_FOUND.
   */
  @Transactional
  public ProjectRecordResponse create(
      Long userId, Long projectId, ProjectRecordCreateRequest request) {
    Project project =
        projectRepository
            .findByIdAndUserId(projectId, userId)
            .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));

    if (!request.aiOrganize()) {
      return createSaved(userId, project, request);
    }
    return createDraft(project, request);
  }

  /**
   * '이대로 저장': 바로 SAVED로 저장하고 프로젝트 카드 1장을 만든다.
   *
   * <p>확인(자료·역량)은 저장 전에 한다. 카드 생성까지 한 트랜잭션이라, 카드가 실패하면 기록 저장도 되돌아간다(SAVED인데 카드 없는 기록이 생기지 않음).
   */
  private ProjectRecordResponse createSaved(
      Long userId, Project project, ProjectRecordCreateRequest request) {
    ProjectRecord record =
        ProjectRecord.builder()
            .project(project)
            .status(ProjectRecordStatus.SAVED)
            .memo(request.memo())
            .did(request.did())
            .concern(request.concern())
            .decision(request.decision())
            .result(request.result())
            .nextStep(request.nextStep())
            .aiOrganized(false)
            .build();
    // H-14: 5칸과 첨부가 모두 비어 있으면 저장하지 않음 (메모는 조건에 없음)
    if (!record.hasFiveFields() && !request.hasAttachments()) {
      throw new BusinessException(ErrorCode.EMPTY_MATERIAL);
    }
    List<Long> competencyIds = validateCompetencyCount(request.competencyIds());
    // TODO(역량 담당): CompetencyService가 생기면 위 개수 확인을 지우고 이 호출로 바꾼다 (1~3·내 파트 확인).
    // competencyService.validate(userId, competencyIds, true);

    ProjectRecord saved = projectRecordRepository.save(record);

    // TODO(첨부 담당): AttachmentService가 생기면 호출한다. 내 첨부인지 확인도 여기서 된다.
    // attachmentService.connect(userId, request.attachmentIds(), owner);

    // TODO(카드 담당): StoryCardService가 생기면(PR #7) 호출하고 cardId를 응답에 넣는다.
    // Long cardId = storyCardService.createFromRecord(
    //     new RecordCardSource(userId, project.getId(), saved.getId(), ...), competencyIds);

    // TODO(역량 담당): competencyService.names(competencyIds)로 태그 이름을 채운다.
    return ProjectRecordResponse.of(saved, null, List.of(), List.of());
  }

  /**
   * 역량 태그 개수 확인(1~3개, 같은 ID는 1개로 셈). 아니면 COMPETENCY_LIMIT.
   *
   * <p>CompetencyService.validate가 생기기 전까지 쓰는 임시 확인이다. 한 규칙이 두 곳에 있으면 어긋나므로, 생기면 지운다.
   */
  private List<Long> validateCompetencyCount(List<Long> competencyIds) {
    List<Long> distinct =
        competencyIds == null ? List.of() : competencyIds.stream().distinct().toList();
    if (distinct.isEmpty() || distinct.size() > 3) {
      throw new BusinessException(ErrorCode.COMPETENCY_LIMIT);
    }
    return distinct;
  }

  /**
   * 'AI와 정리하기': DRAFT 기록만 만들고 카드는 만들지 않는다. 메모나 첨부가 있어야 한다(없으면 EMPTY_MATERIAL).
   *
   * <p>isAiOrganized는 31번(AI 정리) 뒤에 true가 되므로 여기서는 false. competencyIds는 무시한다.
   */
  private ProjectRecordResponse createDraft(Project project, ProjectRecordCreateRequest request) {
    ProjectRecord record =
        ProjectRecord.builder()
            .project(project)
            .status(ProjectRecordStatus.DRAFT)
            .memo(request.memo())
            .did(request.did())
            .concern(request.concern())
            .decision(request.decision())
            .result(request.result())
            .nextStep(request.nextStep())
            .aiOrganized(false)
            .build();
    if (!record.hasMemo() && !request.hasAttachments()) {
      throw new BusinessException(ErrorCode.EMPTY_MATERIAL);
    }
    ProjectRecord saved = projectRecordRepository.save(record);

    // TODO(첨부 담당): AttachmentService가 생기면 호출한다. 내 첨부인지 확인도 여기서 된다.
    // attachmentService.connect(userId, request.attachmentIds(), owner);

    return ProjectRecordResponse.of(saved, null, List.of(), List.of());
  }

  /** 기록 주인 회원 ID. 다른 도메인이 소유 확인에 쓴다. 없으면 NOT_FOUND. */
  public Long getOwnerId(Long recordId) {
    return projectRecordRepository
        .findUserIdById(recordId)
        .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
  }

  /**
   * 기록을 지운다. 24번(카드 삭제)에서 카드 담당 컨트롤러가 부르고, 29번(기록 삭제)에서도 쓴다.
   *
   * <p>남의 기록이거나 없으면 NOT_FOUND. 부르는 쪽 트랜잭션에 묶인다(하나라도 실패하면 모두 되돌림).
   *
   * <p>기록을 가리키는 카드·첨부·AI 질문이 남아 있으면 DB가 기록 삭제를 막으므로(FK, CASCADE 없음) 이것들을 먼저 지운다. 카드를 지우면 역량
   * 태그(card_competency)와 자소서 연결(cover_letter_question_card)은 DB CASCADE로 함께 지워진다.
   */
  @Transactional
  public void delete(Long userId, Long recordId) {
    Long ownerId = getOwnerId(recordId);
    if (!ownerId.equals(userId)) {
      throw new BusinessException(ErrorCode.NOT_FOUND);
    }

    // TODO(카드 담당): StoryCardService가 생기면 호출한다.
    // storyCardService.deleteByRecord(recordId);

    // TODO(첨부 담당): AttachmentService가 생기면 호출한다. owner 형태는 아직 정해지지 않았다. S3 파일까지 지운다.
    // attachmentService.deleteAll(owner);

    // TODO(회고 담당): AiQuestionService가 생기면 호출한다. owner 형태는 아직 정해지지 않았다.
    // aiQuestionService.deleteAll(owner);

    projectRecordRepository.deleteById(recordId);
  }

  /** 기록 ID → 기록 날짜(만든 날). 없는 ID는 결과에서 빠진다. */
  public Map<Long, LocalDate> findDates(Collection<Long> recordIds) {
    if (recordIds == null || recordIds.isEmpty()) {
      return Map.of();
    }
    return projectRecordRepository.findDatesByIdIn(recordIds).stream()
        .collect(Collectors.toMap(RecordDate::recordId, r -> r.createdAt().toLocalDate()));
  }
}
