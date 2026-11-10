package com.sajarok.domain.question.service;

import com.sajarok.domain.question.dto.NewQuestion;
import com.sajarok.domain.question.dto.QuestionView;
import com.sajarok.domain.question.entity.QuestionOwner;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AiQuestionService {

  public List<QuestionView> findAll(QuestionOwner owner) {
    return List.of();
  }

  @Transactional
  public List<QuestionView> replace(QuestionOwner owner, List<NewQuestion> questions) {
    return List.of();
  }

  @Transactional
  public void deleteAll(QuestionOwner owner) {}
}
