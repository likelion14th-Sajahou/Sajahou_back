package com.sajarok.domain.matching.repository;

import com.sajarok.domain.matching.entity.CoverLetterQuestionCard;
import com.sajarok.domain.matching.entity.CoverLetterQuestionCardId;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CoverLetterQuestionCardRepository
    extends JpaRepository<CoverLetterQuestionCard, CoverLetterQuestionCardId> {

  /** 여러 문항의 카드 연결(MAIN/SUB)을 한 번에 읽는다 (문항마다 따로 읽지 않음). */
  List<CoverLetterQuestionCard> findAllByIdCoverLetterQuestionIdIn(Collection<Long> questionIds);

  /** 문항의 카드 연결을 DELETE 한 번으로 지운다 (37번: 초안을 다시 만들 때 연결을 새로 넣기 전). */
  @Modifying(flushAutomatically = true)
  @Query("delete from CoverLetterQuestionCard c where c.id.coverLetterQuestionId = :questionId")
  int deleteAllByQuestionId(@Param("questionId") Long questionId);
}
