package com.sajarok.domain.storycard.ai;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

/** 프로젝트 카드 AI 제목 다듬기: 첫 줄만, 감싼 기호는 떼고, 비면 null, 200자까지. */
class ProjectCardTitleResultTest {

  @Test
  @DisplayName("앞뒤 공백·따옴표·앞의 #·끝 마침표를 뗀다")
  void clean_stripsWrappers() {
    assertThat(new ProjectCardTitleResult("  \"N+1 해결\"  ").clean()).isEqualTo("N+1 해결");
    assertThat(new ProjectCardTitleResult("# 「로그인 API 설계」").clean()).isEqualTo("로그인 API 설계");
    assertThat(new ProjectCardTitleResult("Redis 캐시 도입.").clean()).isEqualTo("Redis 캐시 도입");
  }

  @Test
  @DisplayName("여러 줄이면 첫 줄만 쓴다")
  void clean_firstLineOnly() {
    assertThat(new ProjectCardTitleResult("\n로그인 API 설계\n이유: 보안").clean()).isEqualTo("로그인 API 설계");
  }

  @Test
  @DisplayName("비었거나 기호뿐이면 null(제목 빈칸)")
  void clean_blank_null() {
    assertThat(new ProjectCardTitleResult(null).clean()).isNull();
    assertThat(new ProjectCardTitleResult("   ").clean()).isNull();
    assertThat(new ProjectCardTitleResult("\"\"").clean()).isNull();
  }

  @Test
  @DisplayName("200자(story_card.title)를 넘으면 자른다")
  void clean_truncates() {
    assertThat(new ProjectCardTitleResult("가".repeat(250)).clean()).hasSize(200);
  }

  @Test
  @DisplayName("AI가 다른 칸을 더 보내도 읽기에 실패하지 않는다")
  void ignoresUnknownFields() {
    ProjectCardTitleResult result =
        JsonMapper.builder()
            .build()
            .readValue("{\"title\":\"N+1 해결\",\"reason\":\"...\"}", ProjectCardTitleResult.class);

    assertThat(result.clean()).isEqualTo("N+1 해결");
  }
}
