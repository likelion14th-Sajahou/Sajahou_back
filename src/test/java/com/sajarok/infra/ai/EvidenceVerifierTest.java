package com.sajarok.infra.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.sajarok.infra.ai.EvidenceVerifier.Located;
import java.util.Map;
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

  @Test
  @DisplayName("AI가 프롬프트의 칸 이름까지 옮긴 인용문은 칸 이름을 떼고 남긴다 (실습:·한 일:·A1.)")
  void stripsFieldLabel() {
    String material = "우리 팀 프로젝트의 기록 추가 화면을 사용성 테스트했다.\n설문은 8명에게 받았어요.";

    assertThat(EvidenceVerifier.verified(material, "실습: 우리 팀 프로젝트의 기록 추가 화면을 사용성 테스트했다."))
        .isEqualTo("우리 팀 프로젝트의 기록 추가 화면을 사용성 테스트했다.");
    assertThat(EvidenceVerifier.verified(material, "고민·해결: 설문은 8명에게 받았어요"))
        .isEqualTo("설문은 8명에게 받았어요");
    assertThat(EvidenceVerifier.verified(material, "A1. 설문은 8명에게 받았어요."))
        .isEqualTo("설문은 8명에게 받았어요.");
  }

  @Test
  @DisplayName("그대로 있으면 칸 이름처럼 보여도 떼지 않고, 칸 이름을 떼도 없으면 떨어진다")
  void keepsLabelWhenInOriginal_rejectsServerHint() {
    assertThat(EvidenceVerifier.verified("결론: B안으로 간다.", " 결론: B안으로 간다. "))
        .isEqualTo("결론: B안으로 간다.");
    // 서버가 프롬프트에 넣는 안내 문구는 사용자 글이 아니라서 떨어져야 한다
    assertThat(EvidenceVerifier.verified("우리 팀 프로젝트를 테스트했다.", "과제: 과제 회고에서 따로 씀")).isNull();
  }

  private static final String OWN = "실습: 기록 추가 화면을 사용성 테스트했다.\n메모: 상태 정의를 다음에 써 보고 싶다.";
  private static final Map<Long, String> FILES =
      Map.of(49L, "재테스트에서 평균 작성 시간이 7분 30초에서 4분 50초로 줄\n었다.", 50L, "회의록: B안으로 간다.");

  @Test
  @DisplayName("출처 찾기: AI가 적은 출처에 있으면 그대로 둔다")
  void locate_keepsClaimedSource() {
    assertThat(EvidenceVerifier.locate("B안으로 간다.", 50L, OWN, FILES))
        .isEqualTo(new Located("B안으로 간다.", 50L));
    assertThat(EvidenceVerifier.locate("상태 정의를 다음에 써 보고 싶다.", null, OWN, FILES))
        .isEqualTo(new Located("상태 정의를 다음에 써 보고 싶다.", null));
  }

  @Test
  @DisplayName("출처 찾기: AI가 출처를 비웠어도 PDF 문장이면 그 PDF로 바로잡는다")
  void locate_correctsNullToAttachment() {
    assertThat(EvidenceVerifier.locate("재테스트에서 평균 작성 시간이 7분 30초에서 4분 50초로 줄었다.", null, OWN, FILES))
        .isEqualTo(new Located("재테스트에서 평균 작성 시간이 7분 30초에서 4분 50초로 줄었다.", 49L));
  }

  @Test
  @DisplayName("출처 찾기: 다른 첨부 번호나 넘겨주지 않은 번호를 적어도 실제로 있는 곳으로 바로잡는다")
  void locate_correctsWrongAttachment() {
    assertThat(EvidenceVerifier.locate("B안으로 간다", 49L, OWN, FILES))
        .isEqualTo(new Located("B안으로 간다", 50L));
    assertThat(EvidenceVerifier.locate("실습: 기록 추가 화면을 사용성 테스트했다.", 999L, OWN, FILES))
        .isEqualTo(new Located("실습: 기록 추가 화면을 사용성 테스트했다.", null));
  }

  @Test
  @DisplayName("출처 찾기: 어디에도 없는 문장(지어낸 것)이나 빈 인용문은 null")
  void locate_rejectsMadeUpQuote() {
    assertThat(EvidenceVerifier.locate("평균 작성 시간이 절반으로 줄었다", 49L, OWN, FILES)).isNull();
    assertThat(EvidenceVerifier.locate(null, 49L, OWN, FILES)).isNull();
    assertThat(EvidenceVerifier.locate("기록 추가", null, null, Map.of())).isNull();
  }
}
