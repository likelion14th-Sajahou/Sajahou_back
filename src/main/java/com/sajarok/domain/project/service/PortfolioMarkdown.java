package com.sajarok.domain.project.service;

import com.sajarok.domain.project.dto.PortfolioDraftResponse;
import com.sajarok.domain.project.entity.Project;
import com.sajarok.domain.project.entity.ProjectRole;
import com.sajarok.domain.project.entity.ProjectStatus;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 33번 포트폴리오 초안 Markdown 조립. AI가 준 항목별 본문에 제목·기간·역할 줄·항목 제목·빈칸 문구를 서버가 붙인다.
 *
 * <p>빈칸 문구는 프로토타입 기준. 본문이 없는 항목은 빈칸 문구를 넣고 blanks에 key를 담는다(⚑).
 */
final class PortfolioMarkdown {

  private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy.MM.dd");

  /** 항목 key → (## 제목, 빈칸 문구). PortfolioDraftResult.KEYS와 같은 순서. */
  private static final List<Item> ITEMS =
      List.of(
          new Item("role", "내 역할", "(내 역할을 한 문장으로 더해 주세요)"),
          new Item("contribution", "주요 기여", "(내가 직접 만든 것을 더해 주세요)"),
          new Item("problem", "문제와 해결", "(부딪힌 문제와 해결 방법을 더해 주세요)"),
          new Item("result", "결과", "(결과를 더해 주세요)"),
          new Item("artifact", "산출물", "(발표 자료나 시연 링크를 더해 주세요)"),
          new Item("learned", "배운 점", "(한 줄 남겨 주세요)"));

  private record Item(String key, String heading, String placeholder) {}

  private PortfolioMarkdown() {}

  /**
   * @param bodies key → 본문(없으면 null). PortfolioDraftResult.validate의 결과
   */
  static PortfolioDraftResponse build(Project project, Map<String, String> bodies) {
    StringBuilder sb = new StringBuilder();
    sb.append("# ").append(title(project)).append("\n\n");
    sb.append("- 기간: ").append(period(project)).append('\n');
    if (project.getRoles() != null && !project.getRoles().isEmpty()) {
      sb.append("- 역할: ").append(roles(project.getRoles())).append('\n');
    }

    List<String> blanks = new ArrayList<>();
    for (Item item : ITEMS) {
      String body = bodies.get(item.key());
      sb.append("\n## ").append(item.heading()).append('\n');
      if (body == null) {
        sb.append(item.placeholder()).append('\n');
        blanks.add(item.key());
      } else {
        sb.append(body).append('\n');
      }
    }
    return new PortfolioDraftResponse(sb.toString(), blanks);
  }

  /** "사자록 · 멋사 회고 서비스". 부제가 없으면 이름만. */
  private static String title(Project project) {
    String subtitle = project.getSubtitle();
    return subtitle == null || subtitle.isBlank()
        ? project.getName()
        : project.getName() + " · " + subtitle;
  }

  /**
   * "시작일 ~ 진행 중", 완료면 "시작일 ~ 끝난 날". 시작일은 프로젝트를 만든 날.
   *
   * <p>완료 시각 컬럼이 없어서 끝난 날은 포트폴리오를 마지막으로 저장한 날(portfolio_saved_at)로 쓴다.
   */
  private static String period(Project project) {
    String start = format(project.getCreatedAt());
    LocalDateTime end = project.getPortfolioSavedAt();
    if (project.getStatus() != ProjectStatus.DONE || end == null) {
      return start + " ~ 진행 중";
    }
    return start + " ~ " + format(end);
  }

  private static String format(LocalDateTime dateTime) {
    return dateTime == null ? "" : dateTime.format(DATE);
  }

  /** ["PM", "BACKEND"] → "PM·백엔드" */
  static String roles(List<ProjectRole> roles) {
    return String.join(
        "·",
        roles.stream()
            .map(
                role ->
                    switch (role) {
                      case PM -> "PM";
                      case PLANNING -> "기획";
                      case DESIGN -> "디자인";
                      case FRONTEND -> "프론트엔드";
                      case BACKEND -> "백엔드";
                    })
            .toList());
  }
}
