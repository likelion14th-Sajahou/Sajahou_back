package com.sajarok.domain.matching.repository;

import com.sajarok.domain.matching.entity.CoverLetterQuestionCard;
import com.sajarok.domain.matching.entity.CoverLetterQuestionCardId;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CoverLetterQuestionCardRepository
    extends JpaRepository<CoverLetterQuestionCard, CoverLetterQuestionCardId> {}
