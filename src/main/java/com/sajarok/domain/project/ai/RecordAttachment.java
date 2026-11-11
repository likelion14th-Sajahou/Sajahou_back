package com.sajarok.domain.project.ai;

/**
 * 30번 프롬프트에 넣는 기록 첨부 하나. 추출이 끝난(DONE) 첨부만 만든다.
 *
 * @param type FILE / LINK
 * @param category MEETING_NOTE / PRESENTATION / DESIGN / OUTPUT / OTHER
 */
public record RecordAttachment(Long id, String type, String category, String name, String text) {}
