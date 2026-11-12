package com.sajarok.domain.matching.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sajarok.domain.matching.ai.MatchingPrompts;
import com.sajarok.domain.matching.ai.MatchingPrompts.CompetencyOption;
import com.sajarok.domain.matching.ai.MatchingPrompts.PostingText;
import com.sajarok.domain.matching.ai.PostingAnalysisResult;
import com.sajarok.domain.matching.ai.PostingAnalysisResult.Competency;
import com.sajarok.global.exception.BusinessException;
import com.sajarok.global.exception.ErrorCode;
import com.sajarok.infra.ai.AiClient;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** 36번 PostingAnalyzer 테스트. 실제 OpenAI 대신 가짜 AiClient를 쓴다. */
@ExtendWith(MockitoExtension.class)
class PostingAnalyzerTest {

  @Mock private AiClient aiClient;

  @InjectMocks private PostingAnalyzer analyzer;

  private static final PostingText POSTING =
      new PostingText("카카오", "백엔드", "다양한 직군과 협업한 경험", null, null, null);
  private static final List<CompetencyOption> COMPETENCIES =
      List.of(
          new CompetencyOption(3L, "협업", "팀과 함께 일하며 결과를 만듦"),
          new CompetencyOption(7L, "API 설계", "요청·응답 규칙 설계"));

  private PostingAnalysisResult analyze() {
    return analyzer.analyze(
        POSTING, List.of("REST API 설계 경험 우대"), COMPETENCIES, List.of("협업 경험을 써 주세요"));
  }

  @Test
  @DisplayName("근거 문장은 공고 칸과 첨부 글자에서 찾고, 어디에도 없으면 역량은 두고 근거만 null로 바꾼다")
  void analyze_checksEvidenceAgainstPostingAndAttachments() {
    when(aiClient.call(
            eq(MatchingPrompts.POSTING_ANALYSIS_SYSTEM),
            anyString(),
            eq(PostingAnalysisResult.class)))
        .thenReturn(
            new PostingAnalysisResult(
                List.of(
                    new Competency(3L, "다양한 직군과 협업한 경험"),
                    new Competency(7L, "REST API 설계 경험 우대"),
                    new Competency(7L, "MSA 경험 필수")),
                List.of()));

    assertThat(analyze().competencies())
        .extracting(Competency::evidenceQuote)
        .containsExactly("다양한 직군과 협업한 경험", "REST API 설계 경험 우대", null);
  }

  @Test
  @DisplayName("목록 밖 역량 ID면 AI_FAILED, AiClient의 AI_TIMEOUT은 그대로 올린다")
  void analyze_invalidOrTimeout_throws() {
    when(aiClient.call(anyString(), anyString(), eq(PostingAnalysisResult.class)))
        .thenReturn(new PostingAnalysisResult(List.of(new Competency(99L, "q")), List.of()))
        .thenThrow(new BusinessException(ErrorCode.AI_TIMEOUT));

    assertThatThrownBy(this::analyze).extracting("errorCode").isEqualTo(ErrorCode.AI_FAILED);
    assertThatThrownBy(this::analyze).extracting("errorCode").isEqualTo(ErrorCode.AI_TIMEOUT);
  }

  @Test
  @DisplayName("프롬프트에는 공고 첨부를 6,000자까지만 넣지만, evidenceQuote는 자르지 않은 전체 첨부 글에서 찾는다")
  void analyze_longAttachment_checksEvidenceAgainstFullText() {
    when(aiClient.call(
            eq(MatchingPrompts.POSTING_ANALYSIS_SYSTEM),
            anyString(),
            eq(PostingAnalysisResult.class)))
        .thenReturn(
            new PostingAnalysisResult(List.of(new Competency(3L, "타 직군과 협업 경험 우대")), List.of()));

    PostingAnalysisResult result =
        analyzer.analyze(
            POSTING,
            List.of("가".repeat(6000) + "타 직군과 협업 경험 우대"),
            COMPETENCIES,
            List.of("협업 경험을 써 주세요"));

    ArgumentCaptor<String> user = ArgumentCaptor.forClass(String.class);
    verify(aiClient).call(anyString(), user.capture(), eq(PostingAnalysisResult.class));
    assertThat(user.getValue()).doesNotContain("타 직군과 협업 경험 우대");
    assertThat(result.competencies())
        .extracting(Competency::evidenceQuote)
        .containsExactly("타 직군과 협업 경험 우대");
  }
}
