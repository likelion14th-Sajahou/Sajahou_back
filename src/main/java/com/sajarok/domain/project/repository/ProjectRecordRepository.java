package com.sajarok.domain.project.repository;

import com.sajarok.domain.project.entity.ProjectRecord;
import com.sajarok.domain.project.entity.ProjectRecordStatus;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProjectRecordRepository extends JpaRepository<ProjectRecord, Long> {

  /** 여러 프로젝트의 기록 수·마지막 기록 시각을 쿼리 한 번으로 센다 (프로젝트마다 따로 세지 않음). */
  @Query(
      """
      select new com.sajarok.domain.project.repository.RecordStats(
          r.project.id, count(r), max(r.createdAt))
      from ProjectRecord r
      where r.project.id in :projectIds and r.status = :status
      group by r.project.id
      """)
  List<RecordStats> findStats(
      @Param("projectIds") Collection<Long> projectIds,
      @Param("status") ProjectRecordStatus status);
}
