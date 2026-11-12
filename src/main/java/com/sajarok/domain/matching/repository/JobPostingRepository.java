package com.sajarok.domain.matching.repository;

import com.sajarok.domain.matching.entity.JobPosting;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JobPostingRepository extends JpaRepository<JobPosting, Long> {

  /** 내 공고만 찾는다. 없거나 남의 공고면 비어 있다 (둘 다 NOT_FOUND로 처리). */
  Optional<JobPosting> findByIdAndUserId(Long id, Long userId);

  List<JobPosting> findAllByUserId(Long userId);
}
