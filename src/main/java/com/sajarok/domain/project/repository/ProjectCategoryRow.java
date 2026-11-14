package com.sajarok.domain.project.repository;

import com.sajarok.domain.project.entity.ProjectCategory;

/** 프로젝트 ID와 행사 종류만 읽을 때 쓰는 조회 결과. */
public record ProjectCategoryRow(Long projectId, ProjectCategory category) {}
