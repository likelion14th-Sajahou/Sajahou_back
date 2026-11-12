package com.sajarok.domain.attachment.repository;

import com.sajarok.domain.attachment.entity.Attachment;
import com.sajarok.domain.attachment.entity.ExtractStatus;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AttachmentRepository extends JpaRepository<Attachment, Long> {

  Optional<Attachment> findByIdAndUserId(Long id, Long userId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  List<Attachment> findAllByUserIdAndIdInOrderByIdAsc(Long userId, Collection<Long> ids);

  List<Attachment> findAllByRetrospectiveIdInOrderByIdAsc(Collection<Long> ids);

  List<Attachment> findAllByProjectRecordIdInOrderByIdAsc(Collection<Long> ids);

  List<Attachment> findAllByProjectIdInOrderByIdAsc(Collection<Long> ids);

  List<Attachment> findAllByJobPostingIdInOrderByIdAsc(Collection<Long> ids);

  List<Attachment> findAllByUserIdOrderByIdAsc(Long userId);

  @Query(
      """
          select a.id from Attachment a
          where a.retrospectiveId is null
            and a.projectRecordId is null
            and a.projectId is null
            and a.jobPostingId is null
            and a.updatedAt < :cutoff
          order by a.updatedAt, a.id
          """)
  List<Long> findExpiredUnlinkedIds(@Param("cutoff") LocalDateTime cutoff, Pageable pageable);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select a from Attachment a where a.id = :id")
  Optional<Attachment> findByIdForUpdate(@Param("id") Long id);

  @Modifying
  @Query(
      """
          update Attachment a
          set a.extractedText = :text,
              a.extractStatus = :status,
              a.updatedAt = :updatedAt
          where a.id = :attachmentId
            and a.extractStatus = :pending
          """)
  int updateExtractionResult(
      @Param("attachmentId") Long attachmentId,
      @Param("text") String text,
      @Param("status") ExtractStatus status,
      @Param("pending") ExtractStatus pending,
      @Param("updatedAt") LocalDateTime updatedAt);
}
