package com.sajarok.domain.project.repository;

import com.sajarok.domain.project.entity.ProjectRecord;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProjectRecordRepository extends JpaRepository<ProjectRecord, Long> {}
