package com.sajarok.domain.matching.controller;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sajarok.domain.matching.dto.AnswerResponse;
import com.sajarok.domain.matching.dto.AnswerResponse.CardRef;
import com.sajarok.domain.matching.dto.AnswerResponse.MissingCompetency;
import com.sajarok.domain.matching.dto.JobPostingDetailResponse;
import com.sajarok.domain.matching.dto.JobPostingDetailResponse.PostingCompetencyItem;
import com.sajarok.domain.matching.dto.JobPostingDetailResponse.QuestionItem;
import com.sajarok.domain.matching.entity.AnswerDraft.Segment;
import com.sajarok.domain.matching.entity.CoverLetterCardRole;
import com.sajarok.domain.matching.service.JobPostingService;
import com.sajarok.domain.storycard.entity.StoryCardType;
import com.sajarok.global.auth.CurrentUserProvider;
import com.sajarok.global.exception.BusinessException;
import com.sajarok.global.exception.ErrorCode;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 컨트롤러 테스트: 39번 응답 JSON이 API 명세(필드 이름, 상태 코드, 에러 형식)와 같은지 확인한다.
 *
 * <p>보안 설정은 인증 담당 몫이라 테스트에서만 보안 필터를 끈다 (addFilters = false).
 */
@WebMvcTest(JobPostingController.class)
@AutoConfigureMockMvc(addFilters = false)
class JobPostingControllerTest {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private JobPostingService jobPostingService;
  @MockitoBean private CurrentUserProvider currentUserProvider;

  @BeforeEach
  void setUp() {
    when(currentUserProvider.getUserId()).thenReturn(1L);
  }

  @Test
  @DisplayName("39번: 200과 명세 필드(공고·역량·문항, answer는 37번 모양, 초안 없으면 answer null)를 준다")
  void getDetail_returns200WithSpecFields() throws Exception {
    AnswerResponse answer =
        new AnswerResponse(
            55L,
            List.of(
                new Segment("text", "API를 맡았습니다.", 1, null, null),
                new Segment("blank", null, null, "result", "결과를 채워 주세요")),
            10,
            1,
            List.of(
                new CardRef(
                    1,
                    CoverLetterCardRole.MAIN,
                    410L,
                    StoryCardType.PROJECT,
                    "팀 API 연동",
                    null,
                    "사자록")),
            true,
            List.of(new MissingCompetency(4L, null)),
            false);
    when(jobPostingService.getDetail(1L, 21L))
        .thenReturn(
            new JobPostingDetailResponse(
                21L,
                "카카오",
                "백엔드",
                null,
                "jd",
                "우대",
                "인재상",
                List.of(),
                List.of(new PostingCompetencyItem(3L, null, "협업을 중시", 2)),
                List.of(
                    new QuestionItem(55L, 1, "협업 경험을 써 주세요", 700, List.of(3L, 4L), answer, null),
                    new QuestionItem(56L, 2, "지원 동기", null, List.of(), null, null))));

    mockMvc
        .perform(get("/api/v1/job-postings/21"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.jobPostingId").value(21))
        .andExpect(jsonPath("$.data.postingText").value(nullValue()))
        .andExpect(jsonPath("$.data.preferredQualification").value("우대"))
        .andExpect(jsonPath("$.data.attachments.length()").value(0))
        .andExpect(jsonPath("$.data.competencies[0].competencyId").value(3))
        .andExpect(jsonPath("$.data.competencies[0].evidenceQuote").value("협업을 중시"))
        .andExpect(jsonPath("$.data.competencies[0].cardCount").value(2))
        .andExpect(jsonPath("$.data.questions[0].charLimit").value(700))
        .andExpect(jsonPath("$.data.questions[0].competencyIds[1]").value(4))
        .andExpect(jsonPath("$.data.questions[0].editedAnswer").value(nullValue()))
        .andExpect(jsonPath("$.data.questions[0].answer.segments[0].source").value(1))
        .andExpect(jsonPath("$.data.questions[0].answer.segments[0].slot").doesNotExist())
        .andExpect(jsonPath("$.data.questions[0].answer.segments[1].hint").value("결과를 채워 주세요"))
        .andExpect(jsonPath("$.data.questions[0].answer.charCount").value(10))
        .andExpect(jsonPath("$.data.questions[0].answer.blankCount").value(1))
        .andExpect(jsonPath("$.data.questions[0].answer.cards[0].no").value(1))
        .andExpect(jsonPath("$.data.questions[0].answer.cards[0].role").value("MAIN"))
        .andExpect(jsonPath("$.data.questions[0].answer.cards[0].type").value("PROJECT"))
        .andExpect(jsonPath("$.data.questions[0].answer.cards[0].projectName").value("사자록"))
        .andExpect(jsonPath("$.data.questions[0].answer.usesJobPosting").value(true))
        .andExpect(
            jsonPath("$.data.questions[0].answer.missingCompetencies[0].competencyId").value(4))
        .andExpect(jsonPath("$.data.questions[0].answer.noMatchingCard").value(false))
        .andExpect(jsonPath("$.data.questions[1].charLimit").value(nullValue()))
        .andExpect(jsonPath("$.data.questions[1].answer").value(nullValue()));
  }

  @Test
  @DisplayName("39번: 남의 공고거나 없으면 404 NOT_FOUND")
  void getDetail_notFound_returns404() throws Exception {
    when(jobPostingService.getDetail(1L, 21L))
        .thenThrow(new BusinessException(ErrorCode.NOT_FOUND));

    mockMvc
        .perform(get("/api/v1/job-postings/21"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
  }
}
