package com.sajarok.domain.matching.entity;

/** 공고가 원하는 역량 하나. job_posting.competencies JSON 배열의 한 칸. */
public record PostingCompetency(Long competencyId, String evidenceQuote) {}
