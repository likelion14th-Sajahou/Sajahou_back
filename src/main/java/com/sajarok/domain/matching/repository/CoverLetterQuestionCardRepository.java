package com.sajarok.domain.matching.repository;

import com.sajarok.domain.matching.entity.CoverLetterQuestionCard;
import com.sajarok.domain.matching.entity.CoverLetterQuestionCardId;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CoverLetterQuestionCardRepository
    extends JpaRepository<CoverLetterQuestionCard, CoverLetterQuestionCardId> {

  /** 여러 문항의 카드 연결(MAIN/SUB)을 한 번에 읽는다 (문항마다 따로 읽지 않음). */
  List<CoverLetterQuestionCard> findAllByIdCoverLetterQuestionIdIn(Collection<Long> questionIds);
}
