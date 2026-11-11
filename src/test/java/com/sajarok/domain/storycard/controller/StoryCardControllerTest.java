package com.sajarok.domain.storycard.controller;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sajarok.domain.storycard.dto.StoryCardDetailResponse;
import com.sajarok.domain.storycard.dto.StoryCardDetailResponse.CompetencyView;
import com.sajarok.domain.storycard.dto.StoryCardDetailResponse.SourceRecord;
import com.sajarok.domain.storycard.dto.StoryCardListResponse;
import com.sajarok.domain.storycard.dto.StoryCardListResponse.CardItem;
import com.sajarok.domain.storycard.dto.StoryCardUpdateRequest;
import com.sajarok.domain.storycard.entity.StoryCardStatus;
import com.sajarok.domain.storycard.entity.StoryCardType;
import com.sajarok.domain.storycard.service.StoryCardFacade;
import com.sajarok.global.auth.CurrentUserProvider;
import com.sajarok.global.exception.BusinessException;
import com.sajarok.global.exception.ErrorCode;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 컨트롤러 테스트: 스토리 카드 API의 상태 코드와 에러 형식이 API 명세와 같은지 확인한다.
 *
 * <p>서비스는 가짜(@MockitoBean)로 바꾸고, 보안 필터는 테스트에서만 끈다 (addFilters = false).
 */
@WebMvcTest(StoryCardController.class)
@AutoConfigureMockMvc(addFilters = false)
class StoryCardControllerTest {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private StoryCardFacade storyCardFacade;
  @MockitoBean private CurrentUserProvider currentUserProvider;

  @BeforeEach
  void setUp() {
    when(currentUserProvider.getUserId()).thenReturn(1L);
  }

  @Test
  @DisplayName("24번: 지우면 204와 빈 본문을 주고, 로그인한 회원 ID로 서비스를 부른다")
  void delete_returns204() throws Exception {
    mockMvc
        .perform(delete("/api/v1/story-cards/301"))
        .andExpect(status().isNoContent())
        .andExpect(content().string(""));
    verify(storyCardFacade).delete(1L, 301L);
  }

  @Test
  @DisplayName("24번: 없거나 남의 카드면 404 NOT_FOUND를 준다")
  void delete_notMyCard_returns404() throws Exception {
    doThrow(new BusinessException(ErrorCode.NOT_FOUND)).when(storyCardFacade).delete(1L, 999L);

    mockMvc
        .perform(delete("/api/v1/story-cards/999"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
  }

  @Test
  @DisplayName("24번: 카드 ID가 숫자가 아니면 400 VALIDATION_ERROR를 주고, 서비스는 부르지 않는다")
  void delete_invalidId_returns400() throws Exception {
    mockMvc
        .perform(delete("/api/v1/story-cards/abc"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    verifyNoInteractions(storyCardFacade);
  }

  @Test
  @DisplayName(
      "22번: 상세는 200과 명세 필드(competencies의 id·name, sourceRecord의 date 형식, 빈 weekNumber)를 준다")
  void detail_returns200WithSpecFields() throws Exception {
    when(storyCardFacade.getDetail(1L, 412L))
        .thenReturn(
            new StoryCardDetailResponse(
                412L,
                StoryCardType.PROJECT,
                StoryCardStatus.PUBLISHED,
                null,
                3L,
                "사자록",
                "API 명세 확정",
                null,
                "API 명세 확정",
                null,
                null,
                null,
                List.of("situation", "reason", "result", "learned"),
                List.of(new CompetencyView(21L, null)),
                "# API 명세 확정",
                null,
                List.of("2026-11-03 기록"),
                new SourceRecord(77L, LocalDate.of(2026, 11, 3))));

    mockMvc
        .perform(get("/api/v1/story-cards/412"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.cardId").value(412))
        .andExpect(jsonPath("$.data.type").value("PROJECT"))
        .andExpect(jsonPath("$.data.status").value("PUBLISHED"))
        .andExpect(jsonPath("$.data.weekNumber").value(nullValue()))
        .andExpect(jsonPath("$.data.projectName").value("사자록"))
        .andExpect(jsonPath("$.data.blanks[0]").value("situation"))
        .andExpect(jsonPath("$.data.competencies[0].id").value(21))
        .andExpect(jsonPath("$.data.competencies[0].name").value(nullValue()))
        .andExpect(jsonPath("$.data.evidenceSummary[0]").value("2026-11-03 기록"))
        .andExpect(jsonPath("$.data.sourceRecord.recordId").value(77))
        .andExpect(jsonPath("$.data.sourceRecord.date").value("2026-11-03"));
  }

  @Test
  @DisplayName("22번: 없거나 남의 카드면 404 NOT_FOUND를 준다")
  void detail_notMyCard_returns404() throws Exception {
    when(storyCardFacade.getDetail(1L, 999L)).thenThrow(new BusinessException(ErrorCode.NOT_FOUND));

    mockMvc
        .perform(get("/api/v1/story-cards/999"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
  }

  @Test
  @DisplayName("23번: 수정하면 200과 고친 카드 전체(22번과 같은 모양)를 준다")
  void update_returns200WithCard() throws Exception {
    when(storyCardFacade.update(eq(1L), eq(412L), any(StoryCardUpdateRequest.class)))
        .thenReturn(
            new StoryCardDetailResponse(
                412L,
                StoryCardType.PROJECT,
                StoryCardStatus.PUBLISHED,
                null,
                3L,
                "사자록",
                "새 제목",
                null,
                "API 명세 확정",
                null,
                null,
                null,
                List.of("situation", "reason", "result", "learned"),
                List.of(new CompetencyView(5L, null), new CompetencyView(21L, null)),
                "# API 명세 확정",
                null,
                List.of("2026-11-03 기록"),
                new SourceRecord(77L, LocalDate.of(2026, 11, 3))));

    mockMvc
        .perform(
            patch("/api/v1/story-cards/412")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\": \"새 제목\", \"competencyIds\": [5, 21]}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.cardId").value(412))
        .andExpect(jsonPath("$.data.title").value("새 제목"))
        .andExpect(jsonPath("$.data.competencies.length()").value(2));
  }

  @Test
  @DisplayName("23번: 제목이 200자를 넘으면 400 VALIDATION_ERROR와 title 칸의 이유를 주고, 서비스는 부르지 않는다")
  void update_tooLongTitle_returns400() throws Exception {
    String tooLong = "가".repeat(201);

    mockMvc
        .perform(
            patch("/api/v1/story-cards/412")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\": \"" + tooLong + "\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
        .andExpect(jsonPath("$.error.fields[0].field").value("title"));
    verifyNoInteractions(storyCardFacade);
  }

  @Test
  @DisplayName("23번: 태그 개수가 맞지 않으면 400 COMPETENCY_LIMIT를 준다")
  void update_competencyLimit_returns400() throws Exception {
    when(storyCardFacade.update(eq(1L), eq(412L), any(StoryCardUpdateRequest.class)))
        .thenThrow(new BusinessException(ErrorCode.COMPETENCY_LIMIT));

    mockMvc
        .perform(
            patch("/api/v1/story-cards/412")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"competencyIds\": []}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("COMPETENCY_LIMIT"));
  }

  @Test
  @DisplayName("21번: 쿼리 파라미터를 그대로 넘기고, 200과 totalCount·cards를 준다")
  void list_returns200() throws Exception {
    when(storyCardFacade.getList(1L, StoryCardType.PROJECT, null, null, "API"))
        .thenReturn(
            new StoryCardListResponse(
                1,
                List.of(
                    new CardItem(
                        412L,
                        StoryCardType.PROJECT,
                        null,
                        3L,
                        "사자록",
                        "API 명세 확정",
                        List.of(new CompetencyView(21L, null)),
                        List.of("2026-11-03 기록")))));

    mockMvc
        .perform(get("/api/v1/story-cards").param("type", "PROJECT").param("q", "API"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.totalCount").value(1))
        .andExpect(jsonPath("$.data.cards[0].cardId").value(412))
        .andExpect(jsonPath("$.data.cards[0].projectName").value("사자록"))
        .andExpect(jsonPath("$.data.cards[0].weekNumber").value(nullValue()))
        .andExpect(jsonPath("$.data.cards[0].competencies[0].id").value(21));
  }

  @Test
  @DisplayName("21번: type이 정해진 값이 아니면 400 VALIDATION_ERROR를 주고, 서비스는 부르지 않는다")
  void list_unknownType_returns400() throws Exception {
    mockMvc
        .perform(get("/api/v1/story-cards").param("type", "FOO"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    verifyNoInteractions(storyCardFacade);
  }
}
