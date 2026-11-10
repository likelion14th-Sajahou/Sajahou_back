package com.sajarok.domain.matching.entity;

import static org.assertj.core.api.Assertions.assertThat;

import com.sajarok.domain.matching.entity.AnswerDraft.Segment;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 자소서 글자 수 기준: 공백·줄바꿈 포함, 이모지 1글자, 초안의 빈칸은 세지 않음. */
class AnswerDraftTest {

  @Test
  @DisplayName("countChars: 공백·줄바꿈을 포함하고 이모지는 1글자로 센다")
  void countChars_includesWhitespaceAndCountsEmojiAsOne() {
    assertThat(AnswerDraft.countChars("협업 경험\n")).isEqualTo(6);
    assertThat(AnswerDraft.countChars("좋아요👍")).isEqualTo(4);
    assertThat(AnswerDraft.countChars("")).isZero();
    assertThat(AnswerDraft.countChars(null)).isZero();
  }

  @Test
  @DisplayName("charCount: 초안의 문장 조각만 더하고 빈칸 조각은 세지 않는다")
  void charCount_sumsTextSegmentsOnly() {
    AnswerDraft draft =
        new AnswerDraft(
            List.of(
                new Segment("text", "API를 맡았다.", 1, null, null),
                new Segment("blank", null, null, "result", "결과를 채워 주세요"),
                new Segment("text", " 배포했다.", 2, null, null)),
            List.of());

    assertThat(draft.charCount()).isEqualTo(9 + 6);
  }
}
