package com.sajarok.domain.project.entity;

/**
 * 프로젝트 역할. DB·API 값은 enum 이름(PLANNING 등) 그대로 쓰고, label은 서버가 만드는 글(AI 입력, 포트폴리오 Markdown)에만 쓴다. AI
 * 입력에 enum 이름을 넣으면 "PLANNING으로만 적혀 있고…"처럼 화면 문장에 그대로 나와서 한 곳에 둔다.
 */
public enum ProjectRole {
  PM("PM"),
  PLANNING("기획"),
  DESIGN("디자인"),
  FRONTEND("프론트엔드"),
  BACKEND("백엔드");

  private final String label;

  ProjectRole(String label) {
    this.label = label;
  }

  public String label() {
    return label;
  }
}
