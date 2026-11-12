package com.sajarok.domain.project.service;

import com.sajarok.domain.attachment.dto.AttachmentOwner;
import com.sajarok.domain.attachment.dto.AttachmentView;
import com.sajarok.domain.attachment.entity.Attachment;
import com.sajarok.domain.attachment.entity.ExtractStatus;
import com.sajarok.domain.attachment.service.AttachmentService;
import com.sajarok.domain.competency.service.CompetencyService;
import com.sajarok.domain.project.ai.RecordAttachment;
import com.sajarok.domain.project.ai.RecordQuestionResult.Validated;
import com.sajarok.domain.project.dto.ProjectRecordCreateRequest;
import com.sajarok.domain.project.dto.ProjectRecordResponse;
import com.sajarok.domain.project.dto.ProjectRecordResponse.CompetencyTag;
import com.sajarok.domain.project.dto.ProjectRecordUpdateRequest;
import com.sajarok.domain.project.dto.RecordOrganizeResponse;
import com.sajarok.domain.project.dto.RecordQuestionsResponse;
import com.sajarok.domain.project.entity.Project;
import com.sajarok.domain.project.entity.ProjectRecord;
import com.sajarok.domain.project.entity.ProjectRecordStatus;
import com.sajarok.domain.project.entity.RecordQuestionMode;
import com.sajarok.domain.project.repository.ProjectRecordRepository;
import com.sajarok.domain.project.repository.ProjectRepository;
import com.sajarok.domain.project.repository.RecordDate;
import com.sajarok.domain.question.dto.QuestionView;
import com.sajarok.domain.question.entity.QuestionOwner;
import com.sajarok.domain.question.entity.QuestionStatus;
import com.sajarok.domain.question.service.AiQuestionService;
import com.sajarok.domain.storycard.dto.RecordCardInfo;
import com.sajarok.domain.storycard.dto.RecordCardSource;
import com.sajarok.domain.storycard.service.StoryCardService;
import com.sajarok.global.exception.BusinessException;
import com.sajarok.global.exception.ErrorCode;
import com.sajarok.infra.ai.AiLock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProjectRecordService {

  private final ProjectRecordRepository projectRecordRepository;
  private final ProjectRepository projectRepository;
  private final StoryCardService storyCardService;
  private final AiQuestionService aiQuestionService;
  private final RecordOrganizer recordOrganizer;
  private final RecordQuestionGenerator recordQuestionGenerator;
  private final RecordQuestionWriter recordQuestionWriter;
  private final AttachmentService attachmentService;
  private final AiLock aiLock;
  private final CompetencyService competencyService;

  /** 30번 결과. created면 201(새로 만듦), 아니면 200. */
  public record RecordQuestions(boolean created, RecordQuestionsResponse response) {}

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
    return createDraft(userId, project, request);
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
    checkMaterial(record, request.hasAttachments());
    Set<Long> competencyIds = competencyService.validate(userId, request.competencyIds(), true);

    ProjectRecord saved = projectRecordRepository.save(record);
    // 남의 첨부거나 없는 ID면 NOT_FOUND가 나고, 같은 트랜잭션이라 기록 저장도 되돌아간다.
    List<AttachmentView> attachments =
        connectAttachments(userId, saved.getId(), request.attachmentIds());

    Long cardId =
        storyCardService.createFromRecord(
            toCardSource(userId, saved, attachments.size()), competencyIds);

    return ProjectRecordResponse.of(saved, cardId, toCompetencyTags(competencyIds), attachments);
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

    List<AttachmentView> attachments =
        request.attachmentIds() == null
            ? attachmentService.findAll(AttachmentOwner.projectRecord(recordId))
            : replaceAttachments(userId, recordId, request.attachmentIds());
    boolean hasAttachments = !attachments.isEmpty();

    if (record.isDraft()) {
      if (request.competencyIds() == null) {
        return ProjectRecordResponse.of(record, null, List.of(), attachments);
      }
      checkMaterial(record, hasAttachments);
      Set<Long> competencyIds = competencyService.validate(userId, request.competencyIds(), true);
      record.markSaved();
      Long cardId =
          storyCardService.createFromRecord(
              toCardSource(userId, record, attachments.size()), competencyIds);
      return ProjectRecordResponse.of(record, cardId, toCompetencyTags(competencyIds), attachments);
    }

    checkMaterial(record, hasAttachments);
    Set<Long> competencyIds =
        request.competencyIds() == null
            ? null
            : competencyService.validate(userId, request.competencyIds(), true);

    // memo도 source에 담겨 카드 전문에 반영된다. competencyIds가 null이면 카드 태그는 그대로.
    storyCardService.syncFromRecord(
        toCardSource(userId, record, attachments.size()), competencyIds);
    RecordCardInfo card = storyCardService.findByRecordIds(List.of(recordId)).get(recordId);
    return ProjectRecordResponse.of(
        record,
        card == null ? null : card.cardId(),
        card == null ? List.of() : toCompetencyTags(card.competencyIds()),
        attachments);
  }

  /** 첨부를 기록에 연결하고, 연결된 첨부 전체를 돌려준다. 기록이 내 것인지는 부르는 쪽이 먼저 확인한다. */
  private List<AttachmentView> connectAttachments(
      Long userId, Long recordId, List<Long> attachmentIds) {
    AttachmentOwner owner = AttachmentOwner.projectRecord(recordId);
    attachmentService.connect(userId, attachmentIds, owner);
    return attachmentService.findAll(owner);
  }

  /** 28번: 보낸 목록으로 통째로 바꾼다(빠진 첨부는 연결만 풂). 같은 트랜잭션이라 connect가 실패하면 끊은 것도 되돌려진다. */
  private List<AttachmentView> replaceAttachments(
      Long userId, Long recordId, List<Long> attachmentIds) {
    attachmentService.disconnect(AttachmentOwner.projectRecord(recordId));
    return connectAttachments(userId, recordId, attachmentIds);
  }

  /**
   * 자료 확인. 5칸과 첨부가 모두 비면 EMPTY_MATERIAL (메모는 화면에서 안 써서 조건에 없음, H-14·11/12 기능명세).
   *
   * <p>27번(DRAFT·SAVED)·28번이 같이 쓴다.
   */
  private void checkMaterial(ProjectRecord record, boolean hasAttachments) {
    if (!record.hasFiveFields() && !hasAttachments) {
      throw new BusinessException(ErrorCode.EMPTY_MATERIAL);
    }
  }

  private List<CompetencyTag> toCompetencyTags(Collection<Long> competencyIds) {
    if (competencyIds.isEmpty()) {
      return List.of();
    }

    Map<Long, String> names = competencyService.names(competencyIds);
    return competencyIds.stream().map(id -> new CompetencyTag(id, names.get(id))).toList();
  }

  /** 카드 담당에게 넘기는 기록 내용. 27·28번이 같이 쓴다. */
  private RecordCardSource toCardSource(Long userId, ProjectRecord record, int attachmentCount) {
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
        attachmentCount);
  }

  /**
   * 'AI와 정리하기': DRAFT 기록만 만들고 카드는 만들지 않는다. 5칸이나 첨부가 있어야 한다(11/12 기능명세).
   *
   * <p>isAiOrganized는 31번(AI 정리) 뒤에 true가 되므로 여기서는 false. competencyIds는 무시한다.
   */
  private ProjectRecordResponse createDraft(
      Long userId, Project project, ProjectRecordCreateRequest request) {
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
    checkMaterial(record, request.hasAttachments());
    ProjectRecord saved = projectRecordRepository.save(record);
    List<AttachmentView> attachments =
        connectAttachments(userId, saved.getId(), request.attachmentIds());
    return ProjectRecordResponse.of(saved, null, List.of(), attachments);
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

    deleteChildren(recordId);
    projectRecordRepository.deleteById(recordId);
  }

  /**
   * 기록에 딸린 것을 지운다: 카드 → AI 질문 → 첨부 순서. 기록 행은 지우지 않는다(부르는 쪽이 지움). 29·44번이 같이 쓴다.
   *
   * <p>소유 확인을 하지 않는다. 부르는 쪽(29번 delete, 44번 ProjectService.delete)이 먼저 확인한다.
   */
  @Transactional
  public void deleteChildren(Long recordId) {
    storyCardService.deleteByRecord(recordId);
    aiQuestionService.deleteAll(QuestionOwner.projectRecord(recordId));
    attachmentService.deleteAll(AttachmentOwner.projectRecord(recordId));
  }

  /** cutoff보다 먼저 만든 DRAFT 기록 ID를 오래된 순으로 최대 limit개 (DRAFT 정리 스케줄러용). */
  public List<Long> findExpiredDraftIds(LocalDateTime cutoff, int limit) {
    return projectRecordRepository.findIdsByStatusAndCreatedAtBefore(
        ProjectRecordStatus.DRAFT, cutoff, PageRequest.of(0, limit));
  }

  /**
   * 오래된 DRAFT 기록 하나를 딸린 것과 함께 지운다 (DRAFT 정리 스케줄러용). 지웠으면 true.
   *
   * <p>목록을 읽은 뒤 사용자가 저장(SAVED)했거나 이미 지웠을 수 있어서, 트랜잭션 안에서 다시 읽어 아직 DRAFT이고 cutoff보다 오래됐을 때만 지운다.
   * 기록마다 따로 트랜잭션이라 하나가 실패해도 다른 기록 삭제는 되돌리지 않는다.
   */
  @Transactional
  public boolean deleteExpiredDraft(Long recordId, LocalDateTime cutoff) {
    ProjectRecord record = projectRecordRepository.findById(recordId).orElse(null);
    if (record == null || !record.isDraft() || !record.getCreatedAt().isBefore(cutoff)) {
      return false;
    }
    deleteChildren(recordId);
    projectRecordRepository.delete(record);
    return true;
  }

  /**
   * 30번: 기록 AI 질문. 회고 17번과 같은 mode 규칙(PM 결정 11/11).
   *
   * <p>질문이 있을 때 자료 해시가 다르면 AUTO는 409 MATERIAL_CHANGED, CONTINUE는 이어 쓰기(해시 갱신), REGENERATE는 새로 만든다.
   * 자료가 모두 비었거나 AI가 부족하다고 하면 insufficientMaterial로 200(저장 안 함). AI는 트랜잭션 밖에서 부른다.
   */
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  public RecordQuestions createQuestions(
      Long userId, Long recordId, RecordQuestionMode requestedMode) {
    // 잠금 전에 소유부터 확인해 남의 ID로는 잠금을 잡지 않는다. 30·31번이 같은 키.
    checkRecordOwner(userId, recordId);
    return aiLock.run(
        recordLockKey(recordId), () -> createQuestionsLocked(userId, recordId, requestedMode));
  }

  private RecordQuestions createQuestionsLocked(
      Long userId, Long recordId, RecordQuestionMode requestedMode) {
    RecordQuestionMode mode = requestedMode == null ? RecordQuestionMode.AUTO : requestedMode;
    ProjectRecord record =
        projectRecordRepository
            .findByIdAndUserId(recordId, userId)
            .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
    List<RecordAttachment> attachments = readDoneAttachments(userId, recordId);
    String hash = MaterialHash.of(record, attachments);

    List<QuestionView> existing = aiQuestionService.findAll(QuestionOwner.projectRecord(recordId));
    if (!existing.isEmpty() && mode != RecordQuestionMode.REGENERATE) {
      boolean changed = !hash.equals(record.getQuestionInputHash());
      if (changed && mode == RecordQuestionMode.AUTO) {
        throw materialChanged(existing);
      }
      if (changed) {
        recordQuestionWriter.continueWith(recordId, hash);
      }
      return new RecordQuestions(false, RecordQuestionsResponse.of(existing));
    }

    if (!record.hasMemo() && !record.hasFiveFields() && attachments.isEmpty()) {
      return new RecordQuestions(false, RecordQuestionsResponse.insufficient());
    }
    Validated generated = recordQuestionGenerator.generate(record, attachments);
    if (generated.insufficientMaterial()) {
      return new RecordQuestions(false, RecordQuestionsResponse.insufficient());
    }
    return new RecordQuestions(
        true,
        RecordQuestionsResponse.of(
            recordQuestionWriter.replace(recordId, generated.questions(), hash)));
  }

  /**
   * 30번 첨부: 읽는 중(PENDING)이 끝나길 기다린 뒤, DONE 첨부를 ID마다 따로 읽는다. 글자 목록과 순서로 짝짓지 않아 ID·글자가 어긋나지 않는다.
   *
   * <p>TODO(첨부 담당): ID·종류·이름·글자를 같이 주는 메서드가 생기면 아래 세 단계를 한 번으로 바꾼다.
   */
  private List<RecordAttachment> readDoneAttachments(Long userId, Long recordId) {
    AttachmentOwner owner = AttachmentOwner.projectRecord(recordId);
    attachmentService.readTexts(owner); // 기다리기만 하고 결과는 쓰지 않는다.
    return attachmentService.findAll(owner).stream()
        .filter(view -> view.extractStatus() == ExtractStatus.DONE)
        .map(
            view ->
                toRecordAttachment(
                    attachmentService.getOwnedAttachment(userId, view.attachmentId())))
        .toList();
  }

  private static RecordAttachment toRecordAttachment(Attachment a) {
    return new RecordAttachment(
        a.getId(),
        a.getType().name(),
        a.getCategory().name(),
        a.getName() != null ? a.getName() : a.getUrl(),
        a.getExtractedText());
  }

  private static BusinessException materialChanged(List<QuestionView> existing) {
    long answered = existing.stream().filter(q -> q.status() == QuestionStatus.ANSWERED).count();
    return new BusinessException(
        ErrorCode.MATERIAL_CHANGED,
        "자료가 바뀌었어요. 질문을 새로 만들면 지금까지 쓴 답 %d개가 사라져요.".formatted(answered),
        Map.of("answeredCount", answered));
  }

  /**
   * 31번: 기록 AI 정리. 메모·5칸·첨부(DONE)·30번 답(ANSWERED)으로 5칸을 제안한다. 저장하지 않는다(28번이 저장).
   *
   * <p>AI가 최대 50초 걸려서 트랜잭션 없이 돈다. DRAFT·SAVED 모두 부를 수 있다(H-14 'AI 제안'). 자료가 모두 비면 EMPTY_MATERIAL.
   */
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  public RecordOrganizeResponse organize(Long userId, Long recordId) {
    checkRecordOwner(userId, recordId);
    return aiLock.run(recordLockKey(recordId), () -> organizeLocked(userId, recordId));
  }

  static String recordLockKey(Long recordId) {
    return "record:" + recordId;
  }

  /** 주인 ID만 읽는 가벼운 확인. 남의 기록이거나 없으면 NOT_FOUND. */
  private void checkRecordOwner(Long userId, Long recordId) {
    projectRecordRepository
        .findUserIdById(recordId)
        .filter(userId::equals)
        .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
  }

  private RecordOrganizeResponse organizeLocked(Long userId, Long recordId) {
    ProjectRecord record =
        projectRecordRepository
            .findByIdAndUserId(recordId, userId)
            .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
    List<QuestionView> answered =
        aiQuestionService.findAll(QuestionOwner.projectRecord(recordId)).stream()
            .filter(q -> q.status() == QuestionStatus.ANSWERED)
            .toList();
    // 추출 DONE인 글자만 온다(FAILED·SKIPPED 건너뜀). 읽는 중이면 최대 10초 기다린 뒤 ATTACHMENT_PENDING.
    List<String> attachmentTexts =
        attachmentService.readTexts(AttachmentOwner.projectRecord(recordId));

    if (!record.hasMemo()
        && !record.hasFiveFields()
        && attachmentTexts.isEmpty()
        && answered.isEmpty()) {
      throw new BusinessException(ErrorCode.EMPTY_MATERIAL);
    }
    return RecordOrganizeResponse.of(
        record, recordOrganizer.organize(record, answered, attachmentTexts));
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
