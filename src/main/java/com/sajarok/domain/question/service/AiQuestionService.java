package com.sajarok.domain.question.service;

import com.sajarok.domain.question.dto.NewQuestion;
import com.sajarok.domain.question.dto.QuestionView;
import com.sajarok.domain.question.entity.AiQuestion;
import com.sajarok.domain.question.entity.QuestionOwner;
import com.sajarok.domain.question.repository.AiQuestionRepository;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AiQuestionService {

  private final AiQuestionRepository aiQuestionRepository;

  public List<QuestionView> findAll(QuestionOwner owner) {
    List<AiQuestion> questions =
        switch (owner.kind()) {
          case RETROSPECTIVE ->
              aiQuestionRepository.findAllByRetrospectiveIdOrderBySeqAsc(owner.id());
          case PROJECT_RECORD ->
              aiQuestionRepository.findAllByProjectRecordIdOrderBySeqAsc(owner.id());
          case PROJECT -> aiQuestionRepository.findAllByProjectIdOrderBySeqAsc(owner.id());
        };

    return questions.stream().map(QuestionView::from).toList();
  }

  @Transactional
  public List<QuestionView> replace(QuestionOwner owner, List<NewQuestion> questions) {
    deleteAll(owner);

    List<AiQuestion> created = new ArrayList<>();
    int seq = 1;

    for (NewQuestion q : questions) {
      created.add(
          AiQuestion.create(owner, seq++, q.tag(), q.quote(), q.sourceAttachmentId(), q.content()));
    }

    List<AiQuestion> saved = aiQuestionRepository.saveAll(created);
    return saved.stream().map(QuestionView::from).toList();
  }

  @Transactional
  public void deleteAll(QuestionOwner owner) {
    switch (owner.kind()) {
      case RETROSPECTIVE -> aiQuestionRepository.deleteAllByRetrospectiveId(owner.id());
      case PROJECT_RECORD -> aiQuestionRepository.deleteAllByProjectRecordId(owner.id());
      case PROJECT -> aiQuestionRepository.deleteAllByProjectId(owner.id());
    }
    ;
  }
}
