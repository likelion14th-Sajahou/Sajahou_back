package com.sajarok.domain.storycard.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.sajarok.domain.storycard.ai.ProjectCardTitlePrompts;
import com.sajarok.domain.storycard.ai.ProjectCardTitleResult;
import com.sajarok.global.exception.BusinessException;
import com.sajarok.global.exception.ErrorCode;
import com.sajarok.infra.ai.AiClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** 프로젝트 카드 AI 제목: 지은 제목을 다듬어 붙이고, 실패하면 빈칸으로 두되 예외를 내보내지 않는다. */
@ExtendWith(MockitoExtension.class)
class ProjectCardTitlerTest {

  @Mock private StoryCardService storyCardService;
  @Mock private AiClient aiClient;

  @InjectMocks private ProjectCardTitler projectCardTitler;

  private static final String RECORD_TEXT =
      "2026-11-13 기록\n\n## 한 일\n\n게시글 목록 API에서 N+1 문제를 fetch join으로 해결했다.\n";

  @Test
  @DisplayName("AI가 카드 전문을 읽고 지은 제목을 다듬어 카드에 붙인다")
  void name_attachesCleanedTitle() {
    when(storyCardService.findRecordTextToName(412L)).thenReturn(RECORD_TEXT);
    when(aiClient.call(
            eq(ProjectCardTitlePrompts.SYSTEM),
            contains("N+1 문제를 fetch join으로 해결했다."),
            eq(ProjectCardTitleResult.class)))
        .thenReturn(new ProjectCardTitleResult(" \"게시글 목록 N+1 문제를 fetch join으로 해결\" "));

    projectCardTitler.name(412L);

    verify(storyCardService).nameProjectCard(412L, "게시글 목록 N+1 문제를 fetch join으로 해결");
  }

  @Test
  @DisplayName("AI가 실패하거나 시간이 넘으면 제목을 빈칸으로 두고, 저장 요청이 실패하지 않게 예외를 내보내지 않는다")
  void name_aiFailed_leavesBlank() {
    when(storyCardService.findRecordTextToName(412L)).thenReturn(RECORD_TEXT);
    when(aiClient.call(any(), any(), eq(ProjectCardTitleResult.class)))
        .thenThrow(new BusinessException(ErrorCode.AI_TIMEOUT));

    assertThatCode(() -> projectCardTitler.name(412L)).doesNotThrowAnyException();
    verify(storyCardService, never()).nameProjectCard(any(), any());
  }

  @Test
  @DisplayName("AI가 빈 제목을 주면 빈칸으로 둔다")
  void name_blankTitle_leavesBlank() {
    when(storyCardService.findRecordTextToName(412L)).thenReturn(RECORD_TEXT);
    when(aiClient.call(any(), any(), eq(ProjectCardTitleResult.class)))
        .thenReturn(new ProjectCardTitleResult("  "));

    projectCardTitler.name(412L);

    verify(storyCardService, never()).nameProjectCard(any(), any());
  }

  @Test
  @DisplayName("지을 내용이 없으면(이미 제목이 있거나 첨부만 있음) AI를 부르지 않는다")
  void name_nothingToName_skipsAi() {
    when(storyCardService.findRecordTextToName(412L)).thenReturn(null);

    projectCardTitler.name(412L);

    verifyNoInteractions(aiClient);
    verify(storyCardService, never()).nameProjectCard(any(), any());
  }

  @Test
  @DisplayName("제목을 붙이다 생긴 다른 예외도 내보내지 않는다 (기록·카드는 이미 저장됨)")
  void name_unexpectedError_swallowed() {
    when(storyCardService.findRecordTextToName(412L)).thenReturn(RECORD_TEXT);
    when(aiClient.call(any(), any(), eq(ProjectCardTitleResult.class)))
        .thenReturn(new ProjectCardTitleResult("API 명세 확정"));
    doThrow(new IllegalStateException("DB 끊김"))
        .when(storyCardService)
        .nameProjectCard(412L, "API 명세 확정");

    assertThatCode(() -> projectCardTitler.name(412L)).doesNotThrowAnyException();
  }
}
