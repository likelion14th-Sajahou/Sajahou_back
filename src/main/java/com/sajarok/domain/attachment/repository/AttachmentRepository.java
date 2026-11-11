package com.sajarok.domain.attachment.repository;

import com.sajarok.domain.attachment.entity.Attachment;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AttachmentRepository extends JpaRepository<Attachment, Long> {

  Optional<Attachment> findByIdAndUserId(Long id, Long userId);
}
