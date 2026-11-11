package com.sajarok.domain.attachment.repository;

import com.sajarok.domain.attachment.entity.Attachment;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

public interface AttachmentRepository extends JpaRepository<Attachment, Long> {

  Optional<Attachment> findByIdAndUserId(Long id, Long userId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  List<Attachment> findAllByUserIdAndIdInOrderByIdAsc(Long userId, Collection<Long> ids);

  List<Attachment> findAllByRetrospectiveIdInOrderByIdAsc(Collection<Long> ids);

  List<Attachment> findAllByProjectRecordIdInOrderByIdAsc(Collection<Long> ids);

  List<Attachment> findAllByProjectIdInOrderByIdAsc(Collection<Long> ids);

  List<Attachment> findAllByJobPostingIdInOrderByIdAsc(Collection<Long> ids);
}
