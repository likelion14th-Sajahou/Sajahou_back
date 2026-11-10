package com.sajarok.domain.matching.repository;

import com.sajarok.domain.matching.entity.CoverLetterQuestion;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CoverLetterQuestionRepository extends JpaRepository<CoverLetterQuestion, Long> {

  /** 내 공고의 문항만 찾는다. 없거나 남의 문항이면 비어 있다 (둘 다 NOT_FOUND로 처리). */
  @Query(
      "select q from CoverLetterQuestion q"
          + " where q.id = :questionId and q.jobPosting.userId = :userId")
  Optional<CoverLetterQuestion> findByIdAndUserId(
      @Param("questionId") Long questionId, @Param("userId") Long userId);
}
