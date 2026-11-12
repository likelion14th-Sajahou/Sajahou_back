package com.sajarok.domain.retrospective.service;

import com.sajarok.domain.question.dto.QuestionView;
import com.sajarok.domain.retrospective.ai.RetroAttachment;
import com.sajarok.domain.retrospective.ai.RetroPrompts;
import com.sajarok.domain.retrospective.ai.RetroReviewResult;
import com.sajarok.domain.retrospective.entity.Retrospective;
import com.sajarok.domain.storycard.dto.RetroCardDraft;
import com.sajarok.domain.storycard.entity.StoryCardType;
import com.sajarok.domain.user.entity.Part;
import com.sajarok.infra.ai.AiClient;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
@RequiredArgsConstructor
public class RetroReviewGenerator {
  private final AiClient aiClient;
  private final ObjectMapper objectMapper; // tools.jackson.databind.ObjectMapper (Jackson 3)

  public RetroCardDraft generate(
      Retrospective r, Part part, List<RetroAttachment> attachments, List<QuestionView> answered) {
    String user = RetroPrompts.reviewUser(r, part, attachments, answered);
    RetroReviewResult raw =
        aiClient.call(RetroPrompts.REVIEW_SYSTEM, user, RetroReviewResult.class);
    Map<Long, String> attachmentTexts =
        attachments.stream()
            .collect(Collectors.toMap(RetroAttachment::id, RetroAttachment::text, (a, b) -> a));
    RetroReviewResult.Draft d =
        raw.validate(RetroPrompts.reviewSourceText(r, answered), attachmentTexts);

    return new RetroCardDraft(
        r.getUserId(),
        r.getId(),
        r.getWeekId(),
        StoryCardType.valueOf(r.getType().name()),
        d.title(),
        d.situation(),
        d.action(),
        d.reason(),
        d.result(),
        d.learned(),
        RetroPrompts.fullMarkdown(r, d.markdownBody()),
        objectMapper.writeValueAsString(d.evidence())); // Map → JSON 문자열
  }
}
