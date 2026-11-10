package com.sajarok.domain.matching.service;

import com.sajarok.domain.matching.ai.MatchingPrompts;
import com.sajarok.domain.matching.ai.MatchingPrompts.CompetencyOption;
import com.sajarok.domain.matching.ai.MatchingPrompts.PostingText;
import com.sajarok.domain.matching.ai.PostingAnalysisResult;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * 36번 AI 공고 분석: 프롬프트 조립 → AI 호출 → 결과 검증을 한곳에서 한다. 트랜잭션 안에서 부르지 않는다(최대 50초).
 *
 * <p>AI 응답은 {@link PostingAnalysisResult#validate}를 통과해야 쓴다. 실패하면 AI_FAILED, 시간 초과는 AiClient가
 * AI_TIMEOUT으로 던진다.
 */
@Component
public class PostingAnalyzer {

  public PostingAnalysisResult analyze(
      PostingText posting, List<CompetencyOption> competencies, List<String> questions) {
    String user = MatchingPrompts.postingAnalysisUser(posting, competencies, questions);

    // TODO(AI 모듈 담당): AiClient가 생기면 아래 빈 결과 대신 호출한다.
    // PostingAnalysisResult raw =
    //     aiClient.call(MatchingPrompts.POSTING_ANALYSIS_SYSTEM, user,
    //         PostingAnalysisResult.class);
    // 그 전까지는 "역량을 찾지 못함"으로 응답한다 (FE가 36번을 먼저 연동할 수 있게).
    PostingAnalysisResult raw = new PostingAnalysisResult(List.of(), List.of());

    Set<Long> allowed = competencies.stream().map(CompetencyOption::id).collect(Collectors.toSet());
    return raw.validate(allowed, questions.size());
  }
}
