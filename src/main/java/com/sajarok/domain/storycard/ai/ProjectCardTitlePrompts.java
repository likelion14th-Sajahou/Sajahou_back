package com.sajarok.domain.storycard.ai;

/** 프로젝트 카드 제목 AI 프롬프트. 호출은 ProjectCardTitler가 기록 저장(27·28번) 트랜잭션이 끝난 뒤에 한다. */
public final class ProjectCardTitlePrompts {

  private ProjectCardTitlePrompts() {}

  /** system 프롬프트. TODO: 실제 응답 보며 다듬기. */
  public static final String SYSTEM =
      """
      너는 아기사자(교육생)가 쓴 프로젝트 기록 전문을 읽고, 스토리 카드 제목을 지어 주는 도우미다.

      [규칙]
      1. 이 기록에서 한 가장 구체적인 일을 한 줄(30자 안팎)로 쓴다.
      2. 기록에 없는 사실·숫자·결과는 쓰지 않는다.
      3. "11월 13일 기록", "프로젝트 진행"처럼 뻔하게 쓰지 않는다.
      4. "~ 해결", "~ 설계"처럼 명사로 끝낸다. 마침표·따옴표·# 기호는 쓰지 않는다.
      5. 한국어로 쓴다. 기술 이름(fetch join, Redis 등)은 기록에 쓴 대로 둔다.

      [좋은 예]
      입력:
        ## 한 일
        게시글 목록 API에서 N+1 문제를 fetch join으로 해결했다.
        ## 결과·반응
        쿼리가 21개에서 2개로 줄었다.
      출력: {"title":"게시글 목록 N+1 문제를 fetch join으로 해결"}

      [나쁜 예]
      {"title":"성능 개선"} → 무엇을 했는지 알 수 없다.
      {"title":"응답 속도 3배 개선"} → 기록에 "3배"가 없다.

      [출력 형식]
      아래 JSON으로만 답한다. 다른 글은 쓰지 않는다.
      {"title":"..."}
      """;

  /**
   * user 프롬프트.
   *
   * @param recordText 카드 전문(날짜·한 일·고민·해결·선택과 이유·결과·반응·다음·메모)
   */
  public static String user(String recordText) {
    return "[기록 전문]\n" + recordText.strip() + "\n";
  }
}
