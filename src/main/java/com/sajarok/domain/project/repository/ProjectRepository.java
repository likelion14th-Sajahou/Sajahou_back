package com.sajarok.domain.project.repository;

import com.sajarok.domain.project.entity.Project;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProjectRepository extends JpaRepository<Project, Long> {

  /** 내 프로젝트를 만든 순서 최신순으로. */
  List<Project> findAllByUserIdOrderByCreatedAtDescIdDesc(Long userId);
}
