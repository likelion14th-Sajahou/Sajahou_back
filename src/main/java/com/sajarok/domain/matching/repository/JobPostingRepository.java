package com.sajarok.domain.matching.repository;

import com.sajarok.domain.matching.entity.JobPosting;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JobPostingRepository extends JpaRepository<JobPosting, Long> {}
