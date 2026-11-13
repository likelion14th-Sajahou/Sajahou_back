package com.sajarok.infra.ai;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class EvidenceVerifierTest {

  private static final String PDF_TEXT =
      "자료에 나오는 수치는 지난 기수 팀들의 진행 기록을 바탕으로 정리한 예\n시이며, 팀마다 상황이 다르다.\n" + "6명 중 4명이 2칸 이상을   비웠다.";

  @Test
  @DisplayName("원문에 그대로 있으면 통과")
  void exactQuote() {
    assertThat(EvidenceVerifier.isQuoted(PDF_TEXT, "팀마다 상황이 다르다.")).isTrue();
  }

  @Test
  @DisplayName("PDF에서 단어 중간에 줄바꿈이 들어가도, AI가 붙여 쓴 인용문이 통과한다")
  void ignoresLineBreakInsideWord() {
    assertThat(EvidenceVerifier.isQuoted(PDF_TEXT, "정리한 예시이며, 팀마다 상황이 다르다")).isTrue();
  }

  @Test
  @DisplayName("띄어쓰기가 원문과 달라도 통과한다 (더 넣거나 뺀 경우 모두)")
  void ignoresSpacing() {
    assertThat(EvidenceVerifier.isQuoted(PDF_TEXT, "6명 중 4명이 2칸 이상을 비웠다")).isTrue();
    assertThat(EvidenceVerifier.isQuoted(PDF_TEXT, "6명중 4명이 2칸이상을 비웠다")).isTrue();
  }

  @Test
  @DisplayName("AI가 앞뒤에 붙인 따옴표·말줄임표·마침표는 떼고 비교한다")
  void stripsWrappers() {
    assertThat(EvidenceVerifier.isQuoted(PDF_TEXT, "“6명 중 4명이 2칸 이상을 비웠다.”")).isTrue();
    assertThat(EvidenceVerifier.isQuoted(PDF_TEXT, "'…정리한 예시이며…'")).isTrue();
  }

  @Test
  @DisplayName("원문에 없는 문장(지어냈거나 고쳐 쓴 것)은 떨어진다")
  void rejectsMadeUpQuote() {
    assertThat(EvidenceVerifier.isQuoted(PDF_TEXT, "6명 중 5명이 2칸 이상을 비웠다")).isFalse();
    assertThat(EvidenceVerifier.isQuoted(PDF_TEXT, "6명 중 4명이 … 비웠다")).isFalse();
  }

  @Test
  @DisplayName("인용문이 비었거나 따옴표뿐이면 떨어진다")
  void rejectsEmptyQuote() {
    assertThat(EvidenceVerifier.isQuoted(PDF_TEXT, null)).isFalse();
    assertThat(EvidenceVerifier.isQuoted(PDF_TEXT, "  \n ")).isFalse();
    assertThat(EvidenceVerifier.isQuoted(PDF_TEXT, "“…”")).isFalse();
    assertThat(EvidenceVerifier.isQuoted(null, "팀마다")).isFalse();
  }
}
