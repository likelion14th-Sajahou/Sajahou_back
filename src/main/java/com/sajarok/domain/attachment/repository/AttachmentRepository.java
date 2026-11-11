package com.sajarok.domain.attachment.repository;

import com.sajarok.domain.attachment.entity.Attachment;
import com.sajarok.domain.attachment.entity.ExtractStatus;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
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
      @Param("updatedAt") java.time.LocalDateTime updatedAt);
}
