package com.sajarok.domain.project.dto;

import java.util.List;

/**
 * 33번 포트폴리오 초안 응답 (저장하지 않음).
 *
 * @param blanks 빈칸으로 남은 항목 key: role | contribution | problem | result | artifact | learned
 */
public record PortfolioDraftResponse(String markdown, List<String> blanks) {}
