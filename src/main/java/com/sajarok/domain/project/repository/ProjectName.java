package com.sajarok.domain.project.repository;

/** 프로젝트 ID와 이름만 읽을 때 쓰는 조회 결과. */
public record ProjectName(Long projectId, String name) {}
