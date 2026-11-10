package com.sajarok.domain.project.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.sajarok.domain.project.dto.ProjectRecordCreateRequest;
import com.sajarok.domain.project.dto.ProjectRecordResponse;
import com.sajarok.domain.project.dto.ProjectRecordUpdateRequest;
import com.sajarok.domain.project.entity.Project;
import com.sajarok.domain.project.entity.ProjectRecord;
import com.sajarok.domain.project.entity.ProjectRecordStatus;
import com.sajarok.domain.project.entity.ProjectRole;
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
import java.time.LocalDateTime;
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
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ProjectRecordServiceTest {

  @Mock private ProjectRecordRepository projectRecordRepository;
  @Mock private ProjectRepository projectRepository;
  @Mock private StoryCardService storyCardService;
  @Mock private AiQuestionService aiQuestionService;

  @InjectMocks private ProjectRecordService projectRecordService;

  private static ProjectRecordCreateRequest draftRequest(String memo, List<Long> attachmentIds) {
    return new ProjectRecordCreateRequest(
        true, memo, null, null, null, null, null, attachmentIds, List.of(3L, 21L));
  }

  private Project myProject() {
    return Project.builder().userId(1L).name("사자록").roles(List.of(ProjectRole.BACKEND)).build();
  }

  @Test
  @DisplayName("27번 DRAFT: 메모가 있으면 DRAFT·AI 정리 전으로 저장하고, 카드·역량 없이 응답한다 (보낸 역량은 무시)")
  void create_draft_savesDraftWithoutCard() {
    when(projectRepository.findByIdAndUserId(3L, 1L)).thenReturn(Optional.of(myProject()));
    when(projectRecordRepository.save(any(ProjectRecord.class))).thenAnswer(i -> i.getArgument(0));

    ProjectRecordResponse response =
        projectRecordService.create(1L, 3L, draftRequest("회의에서 API 명세 확정", null));

    assertThat(response.status()).isEqualTo(ProjectRecordStatus.DRAFT);
    assertThat(response.isAiOrganized()).isFalse();
    assertThat(response.cardId()).isNull();
    assertThat(response.memo()).isEqualTo("회의에서 API 명세 확정");
    assertThat(response.competencies()).isEmpty();
  }

  @Test
  @DisplayName("27번 DRAFT: 메모가 비어도 첨부가 있으면 저장한다")
  void create_draft_attachmentOnly_saves() {
    when(projectRepository.findByIdAndUserId(3L, 1L)).thenReturn(Optional.of(myProject()));
    when(projectRecordRepository.save(any(ProjectRecord.class))).thenAnswer(i -> i.getArgument(0));

    ProjectRecordResponse response =
        projectRecordService.create(1L, 3L, draftRequest(null, List.of(601L)));

    assertThat(response.status()).isEqualTo(ProjectRecordStatus.DRAFT);
  }

  @Test
  @DisplayName("27번 DRAFT: 메모가 공백뿐이고 첨부도 없으면 EMPTY_MATERIAL이고 저장하지 않는다")
  void create_draft_noMaterial_throwsEmptyMaterial() {
    when(projectRepository.findByIdAndUserId(3L, 1L)).thenReturn(Optional.of(myProject()));

    assertThatThrownBy(() -> projectRecordService.create(1L, 3L, draftRequest("  ", List.of())))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.EMPTY_MATERIAL);
    verify(projectRecordRepository, never()).save(any());
  }

  private static ProjectRecordCreateRequest savedRequest(
      String memo, String did, List<Long> attachmentIds, List<Long> competencyIds) {
    return new ProjectRecordCreateRequest(
        false, memo, did, null, null, null, null, attachmentIds, competencyIds);
  }

  @Test
  @DisplayName("27번 SAVED: 5칸 중 하나만 있어도 SAVED·AI 정리 전으로 저장한다")
  void create_saved_oneField_saves() {
    when(projectRepository.findByIdAndUserId(3L, 1L)).thenReturn(Optional.of(myProject()));
    when(projectRecordRepository.save(any(ProjectRecord.class))).thenAnswer(i -> i.getArgument(0));

    ProjectRecordResponse response =
        projectRecordService.create(1L, 3L, savedRequest(null, "API 명세 확정", null, List.of(3L)));

    assertThat(response.status()).isEqualTo(ProjectRecordStatus.SAVED);
    assertThat(response.isAiOrganized()).isFalse();
    assertThat(response.did()).isEqualTo("API 명세 확정");
  }

  @Test
  @DisplayName("27번 SAVED: 5칸이 비어도 첨부가 있으면 저장하고, 같은 역량 ID가 겹쳐도 1개로 센다")
  void create_saved_attachmentOnly_andDuplicateCompetency_saves() {
    when(projectRepository.findByIdAndUserId(3L, 1L)).thenReturn(Optional.of(myProject()));
    when(projectRecordRepository.save(any(ProjectRecord.class))).thenAnswer(i -> i.getArgument(0));

    ProjectRecordResponse response =
        projectRecordService.create(
            1L, 3L, savedRequest(null, null, List.of(601L), List.of(3L, 3L, 21L, 21L)));

    assertThat(response.status()).isEqualTo(ProjectRecordStatus.SAVED);
  }

  @Test
  @DisplayName("27번 SAVED: 메모만 있으면 EMPTY_MATERIAL이고 저장하지 않는다 (H-14: 메모는 조건에 없음)")
  void create_saved_memoOnly_throwsEmptyMaterial() {
    when(projectRepository.findByIdAndUserId(3L, 1L)).thenReturn(Optional.of(myProject()));

    assertThatThrownBy(
            () -> projectRecordService.create(1L, 3L, savedRequest("메모", " ", null, List.of(3L))))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.EMPTY_MATERIAL);
    verify(projectRecordRepository, never()).save(any());
  }

  @Test
  @DisplayName("27번 SAVED: 역량이 없거나 0개·4개면 COMPETENCY_LIMIT이고 저장하지 않는다")
  void create_saved_competencyCountOutOfRange_throwsCompetencyLimit() {
    when(projectRepository.findByIdAndUserId(3L, 1L)).thenReturn(Optional.of(myProject()));

    for (List<Long> ids : java.util.Arrays.asList(null, List.<Long>of(), List.of(1L, 2L, 3L, 4L))) {
      assertThatThrownBy(
              () -> projectRecordService.create(1L, 3L, savedRequest(null, "한 일", null, ids)))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.COMPETENCY_LIMIT);
    }
    verify(projectRecordRepository, never()).save(any());
  }

  private ProjectRecord record(ProjectRecordStatus status) {
    return ProjectRecord.builder()
        .project(myProject())
        .status(status)
        .memo("원래 메모")
        .did("원래 한 일")
        .concern("원래 고민")
        .build();
  }

  private static ProjectRecordUpdateRequest update(
      String memo, String did, String concern, List<Long> attachmentIds, List<Long> competencyIds) {
    return new ProjectRecordUpdateRequest(
        memo, did, concern, null, null, null, null, attachmentIds, competencyIds);
  }

  @Test
  @DisplayName("28번: memo만 보내면 memo만 바뀌고 나머지 칸·상태는 그대로다")
  void update_memoOnly_changesMemoOnly() {
    ProjectRecord record = record(ProjectRecordStatus.SAVED);
    when(projectRecordRepository.findByIdAndUserId(77L, 1L)).thenReturn(Optional.of(record));

    ProjectRecordResponse response =
        projectRecordService.update(1L, 77L, update("새 메모", null, null, null, null));

    assertThat(response.memo()).isEqualTo("새 메모");
    assertThat(response.did()).isEqualTo("원래 한 일");
    assertThat(response.concern()).isEqualTo("원래 고민");
    assertThat(response.status()).isEqualTo(ProjectRecordStatus.SAVED);
  }

  @Test
  @DisplayName("28번: \"\"(공백 포함)을 보내면 그 칸을 비우고(null), null인 칸은 그대로 둔다")
  void update_emptyString_clearsField() {
    ProjectRecord record = record(ProjectRecordStatus.SAVED);
    when(projectRecordRepository.findByIdAndUserId(77L, 1L)).thenReturn(Optional.of(record));

    ProjectRecordResponse response =
        projectRecordService.update(1L, 77L, update(" ", null, "", null, null));

    assertThat(response.memo()).isNull();
    assertThat(response.concern()).isNull();
    assertThat(response.did()).isEqualTo("원래 한 일");
  }

  @Test
  @DisplayName("28번 DRAFT: 역량 없이 보내면 칸만 바뀌고 DRAFT로 남는다 (AI와 정리 중)")
  void update_draftWithoutCompetencies_staysDraft() {
    ProjectRecord record = record(ProjectRecordStatus.DRAFT);
    when(projectRecordRepository.findByIdAndUserId(77L, 1L)).thenReturn(Optional.of(record));

    ProjectRecordResponse response =
        projectRecordService.update(1L, 77L, update(null, "정리된 한 일", null, null, null));

    assertThat(response.status()).isEqualTo(ProjectRecordStatus.DRAFT);
    assertThat(response.did()).isEqualTo("정리된 한 일");
  }

  @Test
  @DisplayName("28번 DRAFT: 역량을 함께 보내면 '기록 저장'으로 SAVED가 된다 (H-14)")
  void update_draftWithCompetencies_becomesSaved() {
    ProjectRecord record = record(ProjectRecordStatus.DRAFT);
    when(projectRecordRepository.findByIdAndUserId(77L, 1L)).thenReturn(Optional.of(record));

    ProjectRecordResponse response =
        projectRecordService.update(1L, 77L, update(null, null, null, null, List.of(3L, 21L)));

    assertThat(response.status()).isEqualTo(ProjectRecordStatus.SAVED);
  }

  @Test
  @DisplayName("28번 DRAFT: 저장하려는데 5칸·첨부가 모두 비면 EMPTY_MATERIAL이고 DRAFT로 남는다 (memo는 세지 않음)")
  void update_draftSaveWithoutMaterial_throwsEmptyMaterial() {
    ProjectRecord record = record(ProjectRecordStatus.DRAFT);
    when(projectRecordRepository.findByIdAndUserId(77L, 1L)).thenReturn(Optional.of(record));

    assertThatThrownBy(
            () ->
                projectRecordService.update(
                    1L, 77L, update("메모는 있음", "", "", List.of(), List.of(3L))))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.EMPTY_MATERIAL);
    assertThat(record.getStatus()).isEqualTo(ProjectRecordStatus.DRAFT);
  }

  @Test
  @DisplayName("28번 DRAFT: 역량이 4개면 COMPETENCY_LIMIT이고 DRAFT로 남는다")
  void update_draftSaveWithFourCompetencies_throwsCompetencyLimit() {
    ProjectRecord record = record(ProjectRecordStatus.DRAFT);
    when(projectRecordRepository.findByIdAndUserId(77L, 1L)).thenReturn(Optional.of(record));

    assertThatThrownBy(
            () ->
                projectRecordService.update(
                    1L, 77L, update(null, null, null, null, List.of(1L, 2L, 3L, 4L))))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.COMPETENCY_LIMIT);
    assertThat(record.getStatus()).isEqualTo(ProjectRecordStatus.DRAFT);
  }

  @Test
  @DisplayName("28번 SAVED: competencyIds를 빈 배열로 보내면 COMPETENCY_LIMIT")
  void update_savedWithEmptyCompetencies_throwsCompetencyLimit() {
    ProjectRecord record = record(ProjectRecordStatus.SAVED);
    when(projectRecordRepository.findByIdAndUserId(77L, 1L)).thenReturn(Optional.of(record));

    assertThatThrownBy(
            () -> projectRecordService.update(1L, 77L, update(null, null, null, null, List.of())))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.COMPETENCY_LIMIT);
  }

  @Test
  @DisplayName("28번 SAVED: 고쳐서 5칸이 모두 비고 첨부도 빈 배열이면 EMPTY_MATERIAL")
  void update_savedClearingAllFields_throwsEmptyMaterial() {
    ProjectRecord record = record(ProjectRecordStatus.SAVED);
    when(projectRecordRepository.findByIdAndUserId(77L, 1L)).thenReturn(Optional.of(record));

    assertThatThrownBy(
            () -> projectRecordService.update(1L, 77L, update(null, "", "", List.of(), null)))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.EMPTY_MATERIAL);
  }

  @Test
  @DisplayName("28번: 남의 기록이거나 없으면 NOT_FOUND")
  void update_notMine_throwsNotFound() {
    when(projectRecordRepository.findByIdAndUserId(77L, 1L)).thenReturn(Optional.empty());

    assertThatThrownBy(
            () -> projectRecordService.update(1L, 77L, update("메모", null, null, null, null)))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.NOT_FOUND);
  }

  @Test
  @DisplayName("27번: 남의 프로젝트거나 없으면 NOT_FOUND이고 저장하지 않는다")
  void create_notMyProject_throwsNotFound() {
    when(projectRepository.findByIdAndUserId(3L, 1L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> projectRecordService.create(1L, 3L, draftRequest("메모", null)))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.NOT_FOUND);
    verify(projectRecordRepository, never()).save(any());
  }

  @Test
  @DisplayName("getOwnerId: 기록이 있으면 프로젝트 주인 회원 ID를, 없으면 NOT_FOUND를 준다")
  void getOwnerId_returnsOwnerOrThrowsNotFound() {
    when(projectRecordRepository.findUserIdById(10L)).thenReturn(Optional.of(1L));
    when(projectRecordRepository.findUserIdById(999L)).thenReturn(Optional.empty());

    assertThat(projectRecordService.getOwnerId(10L)).isEqualTo(1L);
    assertThatThrownBy(() -> projectRecordService.getOwnerId(999L))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.NOT_FOUND);
  }

  @Test
  @DisplayName("delete: 내 기록이면 지운다")
  void delete_ownRecord_deletes() {
    when(projectRecordRepository.findUserIdById(10L)).thenReturn(Optional.of(1L));

    projectRecordService.delete(1L, 10L);

    verify(projectRecordRepository).deleteById(10L);
  }

  @Test
  @DisplayName("delete: 남의 기록이면 NOT_FOUND이고 아무것도 지우지 않는다")
  void delete_othersRecord_throwsNotFoundAndDeletesNothing() {
    when(projectRecordRepository.findUserIdById(10L)).thenReturn(Optional.of(2L));

    assertThatThrownBy(() -> projectRecordService.delete(1L, 10L))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.NOT_FOUND);
    verify(projectRecordRepository, never()).deleteById(anyLong());
  }

  @Test
  @DisplayName("delete: 없는 기록이면 NOT_FOUND이고 아무것도 지우지 않는다")
  void delete_missingRecord_throwsNotFoundAndDeletesNothing() {
    when(projectRecordRepository.findUserIdById(999L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> projectRecordService.delete(1L, 999L))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.NOT_FOUND);
    verify(projectRecordRepository, never()).deleteById(anyLong());
  }

  @Test
  @DisplayName("findDates: 기록 ID → 기록 날짜(시각을 뺀 날짜) Map을 준다")
  void findDates_returnsIdToDateMap() {
    when(projectRecordRepository.findDatesByIdIn(List.of(10L, 11L)))
        .thenReturn(
            List.of(
                new RecordDate(10L, LocalDateTime.of(2026, 11, 4, 23, 59)),
                new RecordDate(11L, LocalDateTime.of(2026, 11, 5, 0, 1))));

    assertThat(projectRecordService.findDates(List.of(10L, 11L)))
        .isEqualTo(Map.of(10L, LocalDate.of(2026, 11, 4), 11L, LocalDate.of(2026, 11, 5)));
  }

  @Test
  @DisplayName("findDates: 빈 목록이면 쿼리 없이 빈 Map을 준다")
  void findDates_emptyInput_returnsEmptyWithoutQuery() {
    assertThat(projectRecordService.findDates(List.of())).isEmpty();
    verify(projectRecordRepository, never()).findDatesByIdIn(anyCollection());
  }

  // ---- 카드·AI 질문 연결 (PR #7) ----

  @Test
  @DisplayName("27번 SAVED: 기록 내용과 역량 Set으로 카드를 만들고, 받은 cardId를 응답에 넣는다")
  void create_saved_createsCardWithSourceAndCompetencies() {
    when(projectRepository.findByIdAndUserId(3L, 1L)).thenReturn(Optional.of(myProject()));
    when(projectRecordRepository.save(any(ProjectRecord.class))).thenAnswer(i -> i.getArgument(0));
    when(storyCardService.createFromRecord(any(), any())).thenReturn(412L);

    ProjectRecordResponse response =
        projectRecordService.create(
            1L, 3L, savedRequest("메모", "API 명세 확정", null, List.of(3L, 21L, 3L)));

    ArgumentCaptor<RecordCardSource> source = ArgumentCaptor.forClass(RecordCardSource.class);
    verify(storyCardService).createFromRecord(source.capture(), eq(Set.of(3L, 21L)));
    assertThat(source.getValue().userId()).isEqualTo(1L);
    assertThat(source.getValue().memo()).isEqualTo("메모");
    assertThat(source.getValue().did()).isEqualTo("API 명세 확정");
    assertThat(response.cardId()).isEqualTo(412L);
  }

  @Test
  @DisplayName("27번 DRAFT·28번 DRAFT(역량 없음): 카드 서비스를 부르지 않는다")
  void draftPaths_doNotTouchCards() {
    when(projectRepository.findByIdAndUserId(3L, 1L)).thenReturn(Optional.of(myProject()));
    when(projectRecordRepository.save(any(ProjectRecord.class))).thenAnswer(i -> i.getArgument(0));
    when(projectRecordRepository.findByIdAndUserId(77L, 1L))
        .thenReturn(Optional.of(record(ProjectRecordStatus.DRAFT)));

    projectRecordService.create(1L, 3L, draftRequest("메모", null));
    projectRecordService.update(1L, 77L, update(null, "한 일", null, null, null));

    verifyNoInteractions(storyCardService);
  }

  @Test
  @DisplayName("28번 DRAFT→SAVED: 카드를 만들고 받은 cardId를 응답에 넣는다")
  void update_draftToSaved_createsCard() {
    when(projectRecordRepository.findByIdAndUserId(77L, 1L))
        .thenReturn(Optional.of(record(ProjectRecordStatus.DRAFT)));
    when(storyCardService.createFromRecord(any(), eq(Set.of(3L)))).thenReturn(412L);

    ProjectRecordResponse response =
        projectRecordService.update(1L, 77L, update(null, null, null, null, List.of(3L)));

    assertThat(response.cardId()).isEqualTo(412L);
    verify(storyCardService, never()).syncFromRecord(any(), any());
  }

  @Test
  @DisplayName("28번 SAVED: 바뀐 memo를 담아 카드에 반영하고, 역량을 안 보내면 null(태그 유지)로 넘긴다")
  void update_saved_syncsCardWithMemoAndNullCompetencies() {
    ProjectRecord record = record(ProjectRecordStatus.SAVED);
    ReflectionTestUtils.setField(record, "id", 77L);
    when(projectRecordRepository.findByIdAndUserId(77L, 1L)).thenReturn(Optional.of(record));
    when(storyCardService.findByRecordIds(List.of(77L)))
        .thenReturn(Map.of(77L, new RecordCardInfo(77L, 412L, Set.of(3L))));

    ProjectRecordResponse response =
        projectRecordService.update(1L, 77L, update("새 메모", null, null, null, null));

    ArgumentCaptor<RecordCardSource> source = ArgumentCaptor.forClass(RecordCardSource.class);
    verify(storyCardService).syncFromRecord(source.capture(), isNull());
    assertThat(source.getValue().memo()).isEqualTo("새 메모");
    assertThat(source.getValue().did()).isEqualTo("원래 한 일");
    assertThat(response.cardId()).isEqualTo(412L);
    verify(storyCardService, never()).createFromRecord(any(), any());
  }

  @Test
  @DisplayName("29번: 카드 → AI 질문 → 기록 순서로 지운다 (자식부터, FK에 막히지 않게)")
  void delete_deletesChildrenFirst() {
    when(projectRecordRepository.findUserIdById(77L)).thenReturn(Optional.of(1L));

    projectRecordService.delete(1L, 77L);

    InOrder order = inOrder(storyCardService, aiQuestionService, projectRecordRepository);
    order.verify(storyCardService).deleteByRecord(77L);
    order.verify(aiQuestionService).deleteAll(QuestionOwner.projectRecord(77L));
    order.verify(projectRecordRepository).deleteById(77L);
  }

  @Test
  @DisplayName("29번: 남의 기록이면 카드·AI 질문·기록 모두 지우지 않는다")
  void delete_notMine_deletesNothing() {
    when(projectRecordRepository.findUserIdById(77L)).thenReturn(Optional.of(2L));

    assertThatThrownBy(() -> projectRecordService.delete(1L, 77L))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.NOT_FOUND);
    verifyNoInteractions(storyCardService, aiQuestionService);
    verify(projectRecordRepository, never()).deleteById(anyLong());
  }

  @Test
  @DisplayName("deleteChildren(29·44번 공통): 카드 → AI 질문 순서로 지우고, 기록 행은 지우지 않는다")
  void deleteChildren_deletesCardThenQuestions() {
    projectRecordService.deleteChildren(77L);

    InOrder order = inOrder(storyCardService, aiQuestionService);
    order.verify(storyCardService).deleteByRecord(77L);
    order.verify(aiQuestionService).deleteAll(QuestionOwner.projectRecord(77L));
    verify(projectRecordRepository, never()).deleteById(anyLong());
  }

  private static final LocalDateTime CUTOFF = LocalDateTime.of(2026, 11, 9, 12, 0);

  private ProjectRecord recordAt(ProjectRecordStatus status, LocalDateTime createdAt) {
    ProjectRecord record = ProjectRecord.builder().project(myProject()).status(status).build();
    ReflectionTestUtils.setField(record, "id", 77L);
    ReflectionTestUtils.setField(record, "createdAt", createdAt);
    return record;
  }

  @Test
  @DisplayName("DRAFT 정리: 지울 ID는 DRAFT만, 최대 limit개로 조회한다")
  void findExpiredDraftIds_queriesDraftWithLimit() {
    when(projectRecordRepository.findIdsByStatusAndCreatedAtBefore(
            ProjectRecordStatus.DRAFT, CUTOFF, PageRequest.of(0, 100)))
        .thenReturn(List.of(5L, 6L));

    assertThat(projectRecordService.findExpiredDraftIds(CUTOFF, 100)).containsExactly(5L, 6L);
  }

  @Test
  @DisplayName("DRAFT 정리: 오래된 DRAFT면 카드 → AI 질문 → 기록 순서로 지운다")
  void deleteExpiredDraft_oldDraft_deletesChildrenThenRecord() {
    ProjectRecord record = recordAt(ProjectRecordStatus.DRAFT, CUTOFF.minusMinutes(1));
    when(projectRecordRepository.findById(77L)).thenReturn(Optional.of(record));

    assertThat(projectRecordService.deleteExpiredDraft(77L, CUTOFF)).isTrue();

    InOrder order = inOrder(storyCardService, aiQuestionService, projectRecordRepository);
    order.verify(storyCardService).deleteByRecord(77L);
    order.verify(aiQuestionService).deleteAll(QuestionOwner.projectRecord(77L));
    order.verify(projectRecordRepository).delete(record);
  }

  @Test
  @DisplayName("DRAFT 정리: 목록을 읽은 뒤 SAVED로 바뀐 기록은 지우지 않는다")
  void deleteExpiredDraft_becameSaved_skips() {
    when(projectRecordRepository.findById(77L))
        .thenReturn(Optional.of(recordAt(ProjectRecordStatus.SAVED, CUTOFF.minusDays(1))));

    assertThat(projectRecordService.deleteExpiredDraft(77L, CUTOFF)).isFalse();
    verifyNoInteractions(storyCardService, aiQuestionService);
    verify(projectRecordRepository, never()).delete(any(ProjectRecord.class));
  }

  @Test
  @DisplayName("DRAFT 정리: 기준 시각보다 늦게 만든 DRAFT는 지우지 않는다")
  void deleteExpiredDraft_notExpired_skips() {
    when(projectRecordRepository.findById(77L))
        .thenReturn(Optional.of(recordAt(ProjectRecordStatus.DRAFT, CUTOFF)));

    assertThat(projectRecordService.deleteExpiredDraft(77L, CUTOFF)).isFalse();
    verifyNoInteractions(storyCardService, aiQuestionService);
  }

  @Test
  @DisplayName("DRAFT 정리: 이미 지워진 기록은 건너뛴다")
  void deleteExpiredDraft_missing_skips() {
    when(projectRecordRepository.findById(77L)).thenReturn(Optional.empty());

    assertThat(projectRecordService.deleteExpiredDraft(77L, CUTOFF)).isFalse();
    verifyNoInteractions(storyCardService, aiQuestionService);
  }
}
