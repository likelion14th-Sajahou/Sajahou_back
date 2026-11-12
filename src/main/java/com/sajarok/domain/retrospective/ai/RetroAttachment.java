package com.sajarok.domain.retrospective.ai;

/**
 * 17번 프롬프트에 넣는 회고 첨부 하나. 추출이 끝난(DONE) 첨부만 만든다.
 *
 * @param type FILE / LINK
 * @param category SESSION_PDF / PRACTICE / ASSIGNMENT / OTHER 등
 * @param name 파일 이름. 링크면 URL
 * @param text 추출한 글 전체 (프롬프트에는 앞부분만 넣고, 인용 검증은 전체로 한다)
 */
public record RetroAttachment(Long id, String type, String category, String name, String text) {}
