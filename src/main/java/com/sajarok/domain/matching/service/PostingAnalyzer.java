package com.sajarok.domain.matching.service;

import com.sajarok.domain.matching.ai.MatchingPrompts;
import com.sajarok.domain.matching.ai.MatchingPrompts.CompetencyOption;
import com.sajarok.domain.matching.ai.MatchingPrompts.PostingText;
import com.sajarok.domain.matching.ai.PostingAnalysisResult;
import com.sajarok.infra.ai.AiClient;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** 36번 AI 공고 분석: 프롬프트 조립 → AI 호출 → 검증. 트랜잭션 안에서 부르지 않는다(최대 50초). */
@Component
@RequiredArgsConstructor
public class PostingAnalyzer {

  private final AiClient aiClient;

  public PostingAnalysisResult analyze(
      PostingText posting,
      List<String> attachmentTexts,
      List<CompetencyOption> competencies,
      List<String> questions) {
    String user =
        MatchingPrompts.postingAnalysisUser(posting, attachmentTexts, competencies, questions);
    PostingAnalysisResult raw =
        aiClient.call(MatchingPrompts.POSTING_ANALYSIS_SYSTEM, user, PostingAnalysisResult.class);

    Set<Long> allowed = competencies.stream().map(CompetencyOption::id).collect(Collectors.toSet());
    return raw.validate(allowed, questions.size(), originalText(posting, attachmentTexts));
  }

  /** evidenceQuote를 대조할 공고 원문: 공고 칸과 첨부 글자. */
  private static String originalText(PostingText posting, List<String> attachmentTexts) {
    return Stream.concat(
            Stream.of(
                posting.company(),
                posting.department(),
                posting.postingText(),
                posting.jd(),
                posting.preferredQualification(),
                posting.companyInfo()),
            attachmentTexts.stream())
        .filter(Objects::nonNull)
        .collect(Collectors.joining("\n"));
  }
}
