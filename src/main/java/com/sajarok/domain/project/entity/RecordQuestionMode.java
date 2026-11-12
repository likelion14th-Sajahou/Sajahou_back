package com.sajarok.domain.project.entity;

/** 30번 요청 mode. 회고 17번과 같다. */
public enum RecordQuestionMode {
  /** 없으면 만들고, 있으면 그대로. 자료가 바뀌었으면 409 MATERIAL_CHANGED. */
  AUTO,
  /** 자료가 바뀌어도 이전 질문을 이어 쓴다. */
  CONTINUE,
  /** 질문을 새로 만든다 (이전 답은 지워짐). */
  REGENERATE
}
