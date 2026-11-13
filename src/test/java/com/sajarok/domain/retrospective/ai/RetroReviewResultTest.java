package com.sajarok.domain.retrospective.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sajarok.domain.retrospective.ai.RetroReviewResult.Draft;
import com.sajarok.domain.retrospective.ai.RetroReviewResult.Evidence;
import com.sajarok.global.exception.BusinessException;
import com.sajarok.global.exception.ErrorCode;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 19번 AI 응답 검증: 빈 칸은 null, 지어낸 근거는 빼고 칸 내용은 살린다, 제목·본문이 없으면 AI_FAILED. */
class RetroReviewResultTest {

  private static final String SOURCE = "게시글 목록 API에서 N+1 문제를 fetch join으로 해결\n쿼리 21개가 2개로 줄었어요";
  private static final Map<Long, String> ATTACHMENTS =
      Map.of(502L, "지연 로딩과 N+1 문제, fetch join, 배치 사이즈");

  private static RetroReviewResult result(
      String title, String reason, String markdownBody, Map<String, List<Evidence>> evidence) {
    return new RetroReviewResult(
        title,
        "  N+1 문제가 생겼다.  ",
        "fetch join으로 바꿨다.",
        reason,
        "쿼리가 21개에서 2개로 줄었다.",
        "   ",
        markdownBody,
        evidence);
  }

  @Test
  @DisplayName("칸은 앞뒤 공백을 지우고, 공백뿐인 칸은 null(빈칸)로 본다")
  void cleansFields() {
    Draft d = result("  N+1 해결  ", null, "## 실습\n해결했다", null).validate(SOURCE, ATTACHMENTS);

    assertThat(d.title()).isEqualTo("N+1 해결");
    assertThat(d.situation()).isEqualTo("N+1 문제가 생겼다.");
    assertThat(d.reason()).isNull();
    assertThat(d.learned()).isNull();
    assertThat(d.markdownBody()).isEqualTo("## 실습\n해결했다");
    assertThat(d.evidence()).isEmpty();
  }

  @Test
  @DisplayName("원문(회고 자료·답, 첨부)에 그대로 있는 근거만 남긴다. 지어낸 quote나 모르는 첨부 번호는 그 근거만 뺀다")
  void keepsOnlyQuotedEvidence() {
    Map<String, List<Evidence>> evidence =
        Map.of(
            "situation",
                List.of(
                    new Evidence(null, "게시글 목록 API에서 N+1 문제를 fetch join으로 해결"),
                    new Evidence(null, "응답 속도가 3배 빨라짐")),
            "action", List.of(new Evidence(502L, " fetch join ")),
            "result", List.of(new Evidence(999L, "쿼리 21개가 2개로 줄었어요")));

    Draft d = result("N+1 해결", null, "## 실습", evidence).validate(SOURCE, ATTACHMENTS);

    assertThat(d.evidence().get("situation"))
        .containsExactly(new Evidence(null, "게시글 목록 API에서 N+1 문제를 fetch join으로 해결"));
    assertThat(d.evidence().get("action")).containsExactly(new Evidence(502L, "fetch join"));
    assertThat(d.evidence()).doesNotContainKey("result"); // 넘겨주지 않은 첨부 번호
    assertThat(d.result()).isEqualTo("쿼리가 21개에서 2개로 줄었다."); // 칸 내용은 살린다
  }

  @Test
  @DisplayName("근거 quote에 '실습: '·'A1. ' 같은 칸 이름이 붙어 와도 떼고 남긴다")
  void stripsFieldLabelFromEvidence() {
    Map<String, List<Evidence>> evidence =
        Map.of(
            "situation", List.of(new Evidence(null, "실습: 게시글 목록 API에서 N+1 문제를 fetch join으로 해결")),
            "result", List.of(new Evidence(null, "A1. 쿼리 21개가 2개로 줄었어요")));

    Draft d = result("N+1 해결", null, "## 실습", evidence).validate(SOURCE, ATTACHMENTS);

    assertThat(d.evidence().get("situation"))
        .containsExactly(new Evidence(null, "게시글 목록 API에서 N+1 문제를 fetch join으로 해결"));
    assertThat(d.evidence().get("result")).containsExactly(new Evidence(null, "쿼리 21개가 2개로 줄었어요"));
  }

  @Test
  @DisplayName("빈 칸(null)의 근거와 모르는 key는 버린다")
  void dropsEvidenceOfBlankField() {
    Map<String, List<Evidence>> evidence =
        Map.of(
            "reason", List.of(new Evidence(null, "fetch join으로 해결")),
            "unknown", List.of(new Evidence(null, "fetch join으로 해결")));

    Draft d = result("N+1 해결", null, "## 실습", evidence).validate(SOURCE, ATTACHMENTS);

    assertThat(d.evidence()).isEmpty();
  }

  @Test
  @DisplayName("제목이나 회고록 본문이 비면 AI_FAILED")
  void missingTitleOrBody_aiFailed() {
    assertThatThrownBy(() -> result(" ", null, "## 실습", null).validate(SOURCE, ATTACHMENTS))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.AI_FAILED);
    assertThatThrownBy(() -> result("제목", null, null, null).validate(SOURCE, ATTACHMENTS))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.AI_FAILED);
  }

  @Test
  @DisplayName("제목은 200자(story_card.title)까지만 남긴다")
  void truncatesTitle() {
    Draft d = result("가".repeat(250), null, "## 실습", null).validate(SOURCE, ATTACHMENTS);

    assertThat(d.title()).hasSize(200);
  }
}
