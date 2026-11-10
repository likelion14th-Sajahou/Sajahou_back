package com.sajarok.domain.project.repository;

import com.sajarok.domain.project.entity.ProjectRecord;
import com.sajarok.domain.project.entity.ProjectRecordStatus;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
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

  /** 프로젝트의 기록을 상태별로, 만든 순서 최신순으로 (26번: SAVED만). */
  List<ProjectRecord> findAllByProjectIdAndStatusOrderByCreatedAtDescIdDesc(
      Long projectId, ProjectRecordStatus status);

  /** 프로젝트의 기록 ID만 읽는다 (DRAFT 포함, 내용은 읽지 않음). 44번 삭제용. */
  @Query("select r.id from ProjectRecord r where r.project.id = :projectId")
  List<Long> findIdsByProjectId(@Param("projectId") Long projectId);

  /**
   * 프로젝트의 기록을 DELETE 한 번으로 지운다 (44번). 기록을 하나씩 읽지 않는다.
   *
   * <p>flushAutomatically: 앞서 지운 카드·AI 질문이 아직 DB에 반영(flush)되지 않았으면 먼저 반영한다. 안 그러면 이 DELETE가 먼저 나가
   * story_card.project_record_id 등 FK에 막힐 수 있다.
   */
  @Modifying(flushAutomatically = true)
  @Query("delete from ProjectRecord r where r.project.id = :projectId")
  int deleteAllByProjectId(@Param("projectId") Long projectId);

  /** 내 기록만 찾는다. 없거나 남의 기록이면 비어 있다 (둘 다 NOT_FOUND로 처리). */
  @Query("select r from ProjectRecord r where r.id = :recordId and r.project.userId = :userId")
  Optional<ProjectRecord> findByIdAndUserId(
      @Param("recordId") Long recordId, @Param("userId") Long userId);

  /** 기록 주인 회원 ID(= 기록이 속한 프로젝트의 회원)만 읽는다. */
  @Query("select r.project.userId from ProjectRecord r where r.id = :recordId")
  Optional<Long> findUserIdById(@Param("recordId") Long recordId);

  /** 기록 ID와 만든 시각만 읽는다. */
  @Query(
      "select new com.sajarok.domain.project.repository.RecordDate(r.id, r.createdAt)"
          + " from ProjectRecord r where r.id in :recordIds")
  List<RecordDate> findDatesByIdIn(@Param("recordIds") Collection<Long> recordIds);
}
