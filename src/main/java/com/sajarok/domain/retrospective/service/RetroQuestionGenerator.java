package com.sajarok.domain.retrospective.service;

import com.sajarok.domain.retrospective.ai.RetroAttachment;
import com.sajarok.domain.retrospective.ai.RetroPrompts;
import com.sajarok.domain.retrospective.ai.RetroQuestionResult;
import com.sajarok.domain.retrospective.ai.RetroQuestionResult.Validated;
import com.sajarok.domain.retrospective.entity.Retrospective;
import com.sajarok.domain.user.entity.Part;
import com.sajarok.infra.ai.AiClient;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RetroQuestionGenerator {
  private final AiClient aiClient;

  public Validated generate(Retrospective r, Part part, List<RetroAttachment> attachments) {
    String user = RetroPrompts.questionsUser(r, part, attachments);
    RetroQuestionResult raw =
        aiClient.call(RetroPrompts.QUESTIONS_SYSTEM, user, RetroQuestionResult.class);
    Map<Long, String> attachmentTexts =
        attachments.stream()
            .collect(Collectors.toMap(RetroAttachment::id, RetroAttachment::text, (a, b) -> a));
    return raw.validate(RetroPrompts.materialText(r), attachmentTexts);
  }
}
