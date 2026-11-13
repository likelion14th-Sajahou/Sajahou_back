package com.sajarok.infra.ai;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AttachmentBudgetTest {

  @Test
  @DisplayName("합이 4만 자 안이면 모두 그대로 넣는다")
  void fit_underBudget_keepsAll() {
    List<String> texts = List.of("가".repeat(30_000), "나".repeat(9_000), "짧은 첨부");

    assertThat(AttachmentBudget.fit(texts)).isEqualTo(texts);
  }

  @Test
  @DisplayName("첨부가 하나면 4만 자까지 넣는다 (노션 세션 자료 3만 자는 다 들어간다)")
  void fit_singleLongAttachment_cutsAtTotal() {
    String text = "가".repeat(AttachmentBudget.TOTAL_CHARS) + "뒷부분";

    assertThat(AttachmentBudget.fit(List.of(text)))
        .containsExactly("가".repeat(AttachmentBudget.TOTAL_CHARS));
  }

  @Test
  @DisplayName("긴 첨부가 여러 개면 예산을 똑같이 나눈다")
  void fit_twoLongAttachments_splitEvenly() {
    List<String> fitted = AttachmentBudget.fit(List.of("가".repeat(30_000), "나".repeat(30_000)));

    assertThat(fitted).containsExactly("가".repeat(20_000), "나".repeat(20_000));
  }

  @Test
  @DisplayName("짧은 첨부는 다 넣고, 남은 예산은 긴 첨부들이 나눠 쓴다 (순서는 그대로)")
  void fit_shortAttachmentFirst_restGoesToLongOnes() {
    List<String> fitted =
        AttachmentBudget.fit(List.of("가".repeat(50_000), "나".repeat(1_000), "다".repeat(50_000)));

    assertThat(fitted).containsExactly("가".repeat(19_500), "나".repeat(1_000), "다".repeat(19_500));
    assertThat(fitted.stream().mapToInt(String::length).sum())
        .isEqualTo(AttachmentBudget.TOTAL_CHARS);
  }

  @Test
  @DisplayName("추출 글이 null이면 빈 글로 넣는다")
  void fit_nullText_becomesEmpty() {
    assertThat(AttachmentBudget.fit(Arrays.asList(null, "본문"))).containsExactly("", "본문");
  }

  @Test
  @DisplayName("첨부가 없으면 빈 목록")
  void fit_empty() {
    assertThat(AttachmentBudget.fit(List.of())).isEmpty();
  }

  @Test
  @DisplayName("자르는 자리가 이모지(두 글자짜리 문자) 가운데면 그 앞에서 자른다")
  void fit_doesNotSplitSurrogatePair() {
    String text = "가".repeat(9) + "😀" + "뒤";

    assertThat(AttachmentBudget.fit(List.of(text), 10)).containsExactly("가".repeat(9));
  }
}
