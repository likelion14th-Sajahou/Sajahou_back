package com.sajarok.domain.user.service;

import com.sajarok.domain.attachment.service.AttachmentService;
import com.sajarok.domain.matching.repository.JobPostingRepository;
import com.sajarok.domain.matching.service.JobPostingWriter;
import com.sajarok.domain.project.repository.ProjectRepository;
import com.sajarok.domain.project.service.ProjectService;
import com.sajarok.domain.question.entity.QuestionOwner;
import com.sajarok.domain.question.service.AiQuestionService;
import com.sajarok.domain.retrospective.repository.RetrospectiveRepository;
import com.sajarok.domain.storycard.repository.StoryCardRepository;
import com.sajarok.domain.user.repository.UserRepository;
import com.sajarok.global.exception.BusinessException;
import com.sajarok.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserWithdrawalService {

  private final UserRepository userRepository;
  private final AttachmentService attachmentService;
  private final StoryCardRepository storyCardRepository;
  private final ProjectRepository projectRepository;
  private final ProjectService projectService;
  private final RetrospectiveRepository retrospectiveRepository;
  private final AiQuestionService aiQuestionService;
  private final JobPostingRepository jobPostingRepository;
  private final JobPostingWriter jobPostingWriter;

  @Transactional
  public void withdraw(Long userId) {
    var user =
        userRepository
            .findById(userId)
            .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));

    attachmentService.deleteAllByUserId(userId);

    storyCardRepository.deleteAllByUserId(userId);
    storyCardRepository.flush();

    for (var project : projectRepository.findAllByUserIdOrderByCreatedAtDescIdDesc(userId)) {
      projectService.delete(userId, project.getId());
    }

    for (var retrospective : retrospectiveRepository.findAllByUserId(userId)) {
      aiQuestionService.deleteAll(QuestionOwner.retrospective(retrospective.getId()));
      retrospectiveRepository.delete(retrospective);
    }

    for (var posting : jobPostingRepository.findAllByUserId(userId)) {
      jobPostingWriter.discard(posting.getId());
    }

    userRepository.delete(user);
  }
}
