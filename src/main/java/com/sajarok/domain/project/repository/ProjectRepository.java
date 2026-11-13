package com.sajarok.domain.project.repository;

import com.sajarok.domain.project.entity.Project;
import com.sajarok.domain.project.entity.ProjectCategory;
import com.sajarok.domain.project.entity.ProjectStatus;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProjectRepository extends JpaRepository<Project, Long> {

  /** 내 프로젝트를 만든 순서 최신순으로. */
  List<Project> findAllByUserIdOrderByCreatedAtDescIdDesc(Long userId);

  /** 내 프로젝트만 찾는다. 없거나 남의 프로젝트면 비어 있다 (둘 다 NOT_FOUND로 처리). */
  Optional<Project> findByIdAndUserId(Long id, Long userId);

  /** 프로젝트 주인 회원 ID만 읽는다. */
  @Query("select p.userId from Project p where p.id = :projectId")
  Optional<Long> findUserIdById(@Param("projectId") Long projectId);

  /** 프로젝트 이름만 읽는다 (포트폴리오 같은 큰 칸은 읽지 않음). */
  @Query(
      "select new com.sajarok.domain.project.repository.ProjectName(p.id, p.name)"
          + " from Project p where p.id in :projectIds")
  List<ProjectName> findNamesByIdIn(@Param("projectIds") Collection<Long> projectIds);

  /** 이 상태인 내 프로젝트들의 행사 종류 (겹치면 하나). 발자국 스티커용으로 완료(DONE)를 넘긴다. */
  @Query(
      "select distinct p.category from Project p"
          + " where p.userId = :userId and p.status = :status")
  List<ProjectCategory> findCategoriesByUserIdAndStatus(
      @Param("userId") Long userId, @Param("status") ProjectStatus status);
}
