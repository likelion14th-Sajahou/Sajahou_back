package com.sajarok.domain.project.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.sajarok.domain.project.dto.ProjectRecordCreateRequest;
import com.sajarok.domain.project.dto.ProjectRecordResponse;
import com.sajarok.domain.project.dto.ProjectRecordUpdateRequest;
import com.sajarok.domain.project.entity.ProjectRecordStatus;
import com.sajarok.domain.storycard.dto.RecordCardInfo;
import com.sajarok.domain.storycard.service.ProjectCardTitler;
import com.sajarok.domain.storycard.service.StoryCardService;
import com.sajarok.global.exception.BusinessException;
import com.sajarok.global.exception.ErrorCode;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** 27·28번 저장 뒤 카드 제목: 이번 요청에서 카드가 새로 생겼을 때만, 저장이 끝난 다음 AI 제목을 짓는다. */
@ExtendWith(MockitoExtension.class)
class ProjectRecordFacadeTest {

  @Mock private ProjectRecordService projectRecordService;
  @Mock private StoryCardService storyCardService;
  @Mock private ProjectCardTitler projectCardTitler;

  @InjectMocks private ProjectRecordFacade projectRecordFacade;

  private static final ProjectRecordCreateRequest SAVE_NOW =
      new ProjectRecordCreateRequest(
          false, null, "API 명세 확정", null, null, null, null, null, List.of(3L));
  private static final ProjectRecordCreateRequest AI_ORGANIZE =
      new ProjectRecordCreateRequest(true, null, "API 명세 확정", null, null, null, null, null, null);
  private static final ProjectRecordUpdateRequest SAVE_DRAFT =
      new ProjectRecordUpdateRequest(
          null, "API 명세 확정", null, null, null, null, true, null, List.of(3L));

  private static ProjectRecordResponse response(ProjectRecordStatus status, Long cardId) {
    return new ProjectRecordResponse(
        77L,
        status,
        cardId,
        LocalDate.of(2026, 11, 13),
        false,
        null,
        "API 명세 확정",
        null,
        null,
        null,
        null,
        List.of(),
        List.of());
  }

  @Test
  @DisplayName("27번 '이대로 저장': 저장(트랜잭션)이 끝난 다음 새 카드에 AI 제목을 짓는다")
  void create_saved_namesNewCardAfterSave() {
    ProjectRecordResponse saved = response(ProjectRecordStatus.SAVED, 412L);
    when(projectRecordService.create(1L, 3L, SAVE_NOW)).thenReturn(saved);

    assertThat(projectRecordFacade.create(1L, 3L, SAVE_NOW)).isSameAs(saved);

    InOrder order = inOrder(projectRecordService, projectCardTitler);
    order.verify(projectRecordService).create(1L, 3L, SAVE_NOW);
    order.verify(projectCardTitler).name(412L);
  }

  @Test
  @DisplayName("27번 'AI와 정리하기'(DRAFT): 카드가 없으니 제목을 짓지 않는다")
  void create_draft_doesNotName() {
    when(projectRecordService.create(1L, 3L, AI_ORGANIZE))
        .thenReturn(response(ProjectRecordStatus.DRAFT, null));

    projectRecordFacade.create(1L, 3L, AI_ORGANIZE);

    verifyNoInteractions(projectCardTitler);
  }

  @Test
  @DisplayName("27번: 저장이 실패하면 그 오류가 그대로 나가고 AI를 부르지 않는다")
  void create_failed_doesNotName() {
    when(projectRecordService.create(1L, 3L, SAVE_NOW))
        .thenThrow(new BusinessException(ErrorCode.EMPTY_MATERIAL));

    assertThatThrownBy(() -> projectRecordFacade.create(1L, 3L, SAVE_NOW))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.EMPTY_MATERIAL);
    verifyNoInteractions(projectCardTitler);
  }

  @Test
  @DisplayName("28번 DRAFT 저장: 카드가 이번에 생겼으면 저장이 끝난 다음 AI 제목을 짓는다")
  void update_draftSaved_namesNewCard() {
    when(storyCardService.findByRecordIds(List.of(77L))).thenReturn(Map.of());
    when(projectRecordService.update(1L, 77L, SAVE_DRAFT))
        .thenReturn(response(ProjectRecordStatus.SAVED, 412L));

    projectRecordFacade.update(1L, 77L, SAVE_DRAFT);

    verify(projectCardTitler).name(412L);
  }

  @Test
  @DisplayName("28번 SAVED 수정: 이미 있던 카드의 제목은 다시 짓지 않는다 (H-15)")
  void update_savedRecord_keepsTitle() {
    when(storyCardService.findByRecordIds(List.of(77L)))
        .thenReturn(Map.of(77L, new RecordCardInfo(77L, 412L, Set.of(3L))));
    when(projectRecordService.update(1L, 77L, SAVE_DRAFT))
        .thenReturn(response(ProjectRecordStatus.SAVED, 412L));

    projectRecordFacade.update(1L, 77L, SAVE_DRAFT);

    verifyNoInteractions(projectCardTitler);
  }

  @Test
  @DisplayName("28번 DRAFT 칸만 수정: 카드가 생기지 않았으니 제목을 짓지 않는다")
  void update_draftOnly_doesNotName() {
    when(storyCardService.findByRecordIds(List.of(77L))).thenReturn(Map.of());
    when(projectRecordService.update(any(), any(), any()))
        .thenReturn(response(ProjectRecordStatus.DRAFT, null));

    projectRecordFacade.update(1L, 77L, SAVE_DRAFT);

    verifyNoInteractions(projectCardTitler);
  }
}
