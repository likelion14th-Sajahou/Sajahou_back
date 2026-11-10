package com.sajarok.domain.project.service;

import com.sajarok.domain.project.dto.ProjectRecordCreateRequest;
import com.sajarok.domain.project.dto.ProjectRecordResponse;
import com.sajarok.domain.project.dto.ProjectRecordUpdateRequest;
import com.sajarok.domain.project.entity.Project;
import com.sajarok.domain.project.entity.ProjectRecord;
import com.sajarok.domain.project.entity.ProjectRecordStatus;
import com.sajarok.domain.project.repository.ProjectRecordRepository;
import com.sajarok.domain.project.repository.ProjectRepository;
import com.sajarok.domain.project.repository.RecordDate;
import com.sajarok.domain.question.entity.QuestionOwner;
import com.sajarok.domain.question.service.AiQuestionService;
import com.sajarok.domain.storycard.dto.RecordCardInfo;
import com.sajarok.domain.storycard.dto.RecordCardSource;
import com.sajarok.domain.storycard.service.StoryCardService;
import com.sajarok.global.exception.BusinessException;
import com.sajarok.global.exception.ErrorCode;
import java.time.LocalDate;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
  private final StoryCardService storyCardService;
  private final AiQuestionService aiQuestionService;

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
    checkSavedMaterial(record, request.hasAttachments());
    Set<Long> competencyIds = validateCompetencyCount(request.competencyIds());
    // TODO(역량 담당): CompetencyService가 생기면 위 개수 확인을 지우고 이 호출로 바꾼다 (1~3·내 파트 확인).
    // competencyService.validate(userId, competencyIds, true);

    ProjectRecord saved = projectRecordRepository.save(record);

    // TODO(첨부 담당): AttachmentService가 생기면 호출한다. 내 첨부인지 확인도 여기서 된다.
    // attachmentService.connect(userId, request.attachmentIds(), owner);

    Long cardId = storyCardService.createFromRecord(toCardSource(userId, saved), competencyIds);

    // TODO(역량 담당): competencyService.names(competencyIds)로 태그 이름을 채운다.
    return ProjectRecordResponse.of(saved, cardId, List.of(), List.of());
  }

  /**
   * 28번: 기록 수정. 보낸 필드만 바꾼다(저장은 변경 감지). 남의 기록이거나 없으면 NOT_FOUND.
   *
   * <p>DRAFT + competencyIds = '기록 저장'(SAVED로 바꾸고 카드 생성). DRAFT + competencyIds 없음 = 칸만 바꿈(AI와 정리
   * 중). SAVED = 칸을 바꾸고 카드에도 반영(memo는 카드 전문에 반영, 제목·배운 점은 카드 쪽에서 유지, H-15).
   */
  @Transactional
  public ProjectRecordResponse update(
      Long userId, Long recordId, ProjectRecordUpdateRequest request) {
    ProjectRecord record =
        projectRecordRepository
            .findByIdAndUserId(recordId, userId)
            .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));

    record.edit(
        request.memo(),
        request.did(),
        request.concern(),
        request.decision(),
        request.result(),
        request.nextStep(),
        request.isAiOrganized());

    // TODO(첨부 담당): attachmentIds를 보냈으면 연결을 통째로 바꾼다.
    // if (request.attachmentIds() != null) attachmentService.connect(userId,
    // request.attachmentIds(), owner);

    // attachmentIds를 안 보냈으면 기존 첨부가 있을 수 있다. 기존 첨부 수는 첨부 담당 코드가 생기면 확인한다(TODO).
    boolean mayHaveAttachments =
        request.attachmentIds() == null || !request.attachmentIds().isEmpty();

    if (record.isDraft()) {
      if (request.competencyIds() == null) {
        return ProjectRecordResponse.of(record, null, List.of(), List.of());
      }
      checkSavedMaterial(record, mayHaveAttachments);
      Set<Long> competencyIds = validateCompetencyCount(request.competencyIds());
      // TODO(역량 담당): competencyService.validate(userId, competencyIds, true);
      record.markSaved();
      Long cardId = storyCardService.createFromRecord(toCardSource(userId, record), competencyIds);
      // TODO(역량 담당): competencyService.names(competencyIds)로 태그 이름을 채운다.
      return ProjectRecordResponse.of(record, cardId, List.of(), List.of());
    }

    checkSavedMaterial(record, mayHaveAttachments);
    Set<Long> competencyIds =
        request.competencyIds() == null ? null : validateCompetencyCount(request.competencyIds());
    // TODO(역량 담당): competencyIds가 있으면 competencyService.validate(userId, competencyIds, true);

    // memo도 source에 담겨 카드 전문에 반영된다. competencyIds가 null이면 카드 태그는 그대로.
    storyCardService.syncFromRecord(toCardSource(userId, record), competencyIds);
    RecordCardInfo card = storyCardService.findByRecordIds(List.of(recordId)).get(recordId);
    // TODO(역량 담당): card.competencyIds()로 competencyService.names를 불러 태그 이름을 채운다.
    return ProjectRecordResponse.of(
        record, card == null ? null : card.cardId(), List.of(), List.of());
  }

  /**
   * SAVED 기록의 자료 확인. H-14: 5칸과 첨부가 모두 비어 있으면 저장하지 않음 (메모는 조건에 없음). 아니면 EMPTY_MATERIAL.
   *
   * <p>27·28번이 같이 쓴다.
   */
  private void checkSavedMaterial(ProjectRecord record, boolean hasAttachments) {
    if (!record.hasFiveFields() && !hasAttachments) {
      throw new BusinessException(ErrorCode.EMPTY_MATERIAL);
    }
  }

  /**
   * 역량 태그 개수 확인(1~3개, 같은 ID는 1개로 셈). 아니면 COMPETENCY_LIMIT.
   *
   * <p>CompetencyService.validate가 생기기 전까지 쓰는 임시 확인이다. 한 규칙이 두 곳에 있으면 어긋나므로, 생기면 지운다.
   */
  private Set<Long> validateCompetencyCount(List<Long> competencyIds) {
    Set<Long> distinct = competencyIds == null ? Set.of() : new LinkedHashSet<>(competencyIds);
    if (distinct.isEmpty() || distinct.size() > 3) {
      throw new BusinessException(ErrorCode.COMPETENCY_LIMIT);
    }
    return distinct;
  }

  /**
   * 카드 담당에게 넘기는 기록 내용. 27·28번이 같이 쓴다.
   *
   * <p>TODO(첨부 담당): attachmentCount는 첨부 수를 셀 수 있게 되면 채운다. 지금은 0.
   */
  private RecordCardSource toCardSource(Long userId, ProjectRecord record) {
    return new RecordCardSource(
        userId,
        record.getProject().getId(),
        record.getId(),
        record.getCreatedAt() == null ? null : record.getCreatedAt().toLocalDate(),
        record.getMemo(),
        record.getDid(),
        record.getConcern(),
        record.getDecision(),
        record.getResult(),
        record.getNextStep(),
        0);
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

    // 자식부터 지운다: 카드 → AI 질문 → 첨부 → 기록
    storyCardService.deleteByRecord(recordId);
    aiQuestionService.deleteAll(QuestionOwner.projectRecord(recordId));

    // TODO(첨부 담당): AttachmentService가 생기면 호출한다. S3 파일까지 지운다.
    // attachmentService.deleteAll(owner);

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
