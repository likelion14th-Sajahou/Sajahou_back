package com.sajarok.domain.question.repository;

import com.sajarok.domain.question.entity.AiQuestion;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AiQuestionRepository extends JpaRepository<AiQuestion, Long> {

  List<AiQuestion> findAllByRetrospectiveIdOrderBySeqAsc(Long retrospectiveId);

  List<AiQuestion> findAllByProjectRecordIdOrderBySeqAsc(Long projectRecordId);

  List<AiQuestion> findAllByProjectIdOrderBySeqAsc(Long projectId);

  void deleteAllByRetrospectiveId(Long retrospectiveId);

  void deleteAllByProjectRecordId(Long projectRecordId);

  void deleteAllByProjectId(Long projectId);
}
