package com.sajarok.domain.project.entity;

/**
 * 프로젝트 행사 종류 (25번에서 꼭 고름). 이름이 week.phase 값과 같아서, 발자국에서 그 행사의 발표 주를 찾는 데 그대로 쓴다. 완료(DONE)하면 그 행사 발표
 * 주(13·27·38주차)에 스티커가 붙는다.
 */
public enum ProjectCategory {
  IDEATHON,
  HACKATHON,
  JUNGKATHON
}
