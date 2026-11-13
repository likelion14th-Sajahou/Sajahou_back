package com.sajarok.domain.retrospective.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.sajarok.domain.retrospective.entity.Retrospective;
import com.sajarok.domain.retrospective.entity.RetrospectiveType;
import com.sajarok.domain.user.entity.Part;
import com.sajarok.infra.ai.AttachmentBudget;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RetroPromptsTest {

  private static RetroAttachment pdf(Long id, String text) {
    return new RetroAttachment(id, "FILE", "SESSION_PDF", "세션자료.pdf", text);
  }

  @Test
  @DisplayName("17번 프롬프트: 3만 자 세션 자료는 끝까지 다 넣는다")
  void questionsUser_keepsWholeSessionPdf() {
    Retrospective r = Retrospective.start(1L, 37L, RetrospectiveType.SESSION);
    String session = "가".repeat(29_990) + "마지막 문단";

    String user = RetroPrompts.questionsUser(r, Part.PLAN_DESIGN, List.of(pdf(501L, session)));

    assertThat(user).contains(": " + session + "\n");
  }

  @Test
  @DisplayName("17번 프롬프트: 첨부는 다 합쳐 4만 자까지만 넣는다")
  void questionsUser_cutsAtTotalBudget() {
    Retrospective r = Retrospective.start(1L, 37L, RetrospectiveType.SESSION);
    String kept = "가".repeat(AttachmentBudget.TOTAL_CHARS);

    String user = RetroPrompts.questionsUser(r, Part.PLAN_DESIGN, List.of(pdf(501L, kept + "뒷부분")));

    assertThat(user).contains(": " + kept + "\n").doesNotContain("뒷부분");
  }

  @Test
  @DisplayName("19번 프롬프트도 같은 예산: 긴 첨부 두 개는 2만 자씩 넣는다")
  void reviewUser_splitsBudgetAcrossAttachments() {
    Retrospective r = Retrospective.start(1L, 37L, RetrospectiveType.SESSION);
    String first = "가".repeat(30_000);
    String second = "나".repeat(30_000);

    String user =
        RetroPrompts.reviewUser(
            r, Part.PLAN_DESIGN, List.of(pdf(501L, first), pdf(502L, second)), List.of());

    assertThat(user)
        .contains(": " + "가".repeat(20_000) + "\n", ": " + "나".repeat(20_000) + "\n")
        .doesNotContain("가".repeat(20_001), "나".repeat(20_001));
  }
}
