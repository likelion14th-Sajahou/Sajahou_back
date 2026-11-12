package com.sajarok.domain.retrospective.dto;

/** 17번 요청 mode. 프로젝트 기록 30번(RecordQuestionMode)과 같은 규칙(PM 결정 11/11). */
public enum RetroQuestionMode {
  /** 없으면 만들고, 있으면 그대로. 질문을 만든 뒤 자료가 바뀌었으면 409 MATERIAL_CHANGED. */
  AUTO,
  /** 자료가 바뀌어도 이전 질문을 이어 쓴다. 해시만 지금 자료로 바꿔서 다음 AUTO에서 다시 묻지 않는다. */
  CONTINUE,
  /** 질문을 새로 만든다. 이전 질문과 답은 지워진다. */
  REGENERATE
}
