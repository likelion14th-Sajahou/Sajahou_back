package com.sajarok.domain.retrospective.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.sajarok.domain.retrospective.entity.Retrospective;
import com.sajarok.domain.retrospective.entity.RetrospectiveType;
import com.sajarok.domain.user.entity.Part;
import com.sajarok.infra.ai.AttachmentBudget;
import java.util.List;
import java.util.regex.Pattern;
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

  @Test
  @DisplayName("17번 system: 근거는 내용이 담긴 한 문장, 같은 근거 두 번 금지, 회고 자료가 짧으면 첨부 문장을 짚는다")
  void questionsSystem_quoteRules() {
    assertThat(RetroPrompts.QUESTIONS_SYSTEM)
        .contains(
            "quote는 내용이 담긴 한 문장으로 짚는다",
            "제목이나 단어만 있는 줄은 quote로 쓰지 않는다",
            "같은 quote를 두 질문에 쓰지 않는다",
            "[회고 자료]가 제목·단어처럼 짧으면 [첨부](세션 PDF 등)의 문장을 quote로 짚는다");
  }

  @Test
  @DisplayName("17번 system: 좋은 예의 질문들은 서로 다른 quote를 짚는다 (새 규칙과 예시가 어긋나지 않게)")
  void questionsSystem_goodExampleHasDistinctQuotes() {
    String good =
        RetroPrompts.QUESTIONS_SYSTEM.substring(
            RetroPrompts.QUESTIONS_SYSTEM.indexOf("[좋은 예]"),
            RetroPrompts.QUESTIONS_SYSTEM.indexOf("[나쁜 예]"));
    List<String> quotes =
        Pattern.compile("\"quote\":\"([^\"]+)\"")
            .matcher(good)
            .results()
            .map(m -> m.group(1))
            .toList();

    assertThat(quotes).hasSize(4).doesNotHaveDuplicates();
  }

  @Test
  @DisplayName("19번 카드 제목: 그 주차 내 파트의 세션명을 쓴다")
  void cardTitle_usesSessionTitle() {
    Retrospective session = Retrospective.start(1L, 37L, RetrospectiveType.SESSION);
    Retrospective assignment = Retrospective.start(1L, 37L, RetrospectiveType.ASSIGNMENT);

    assertThat(RetroPrompts.cardTitle(session, 10, " JPA 연관관계 ")).isEqualTo("JPA 연관관계");
    assertThat(RetroPrompts.cardTitle(assignment, 10, "JPA 연관관계")).isEqualTo("JPA 연관관계");
  }

  @Test
  @DisplayName("19번 카드 제목: 세션명을 모르면 회고록 제목(10주차 세션 회고)을 쓴다")
  void cardTitle_withoutSessionTitle_usesHeading() {
    Retrospective session = Retrospective.start(1L, 37L, RetrospectiveType.SESSION);
    Retrospective assignment = Retrospective.start(1L, 37L, RetrospectiveType.ASSIGNMENT);

    assertThat(RetroPrompts.cardTitle(session, 10, null)).isEqualTo("10주차 세션 회고");
    assertThat(RetroPrompts.cardTitle(assignment, 10, " ")).isEqualTo("10주차 과제 회고");
    assertThat(RetroPrompts.cardTitle(session, null, null)).isEqualTo("세션 회고");
  }

  @Test
  @DisplayName("19번 system 프롬프트는 AI에게 제목을 쓰라고 하지 않는다 (서버가 세션명으로 붙임)")
  void reviewSystem_doesNotAskForTitle() {
    assertThat(RetroPrompts.REVIEW_SYSTEM).doesNotContain("\"title\"");
  }
}
