package com.sajarok.domain.project.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.sajarok.domain.project.entity.Project;
import com.sajarok.domain.project.entity.ProjectRecord;
import com.sajarok.domain.project.entity.ProjectRecordStatus;
import com.sajarok.domain.project.repository.ProjectRecordRepository;
import com.sajarok.domain.question.dto.NewQuestion;
import com.sajarok.domain.question.entity.QuestionOwner;
import com.sajarok.domain.question.service.AiQuestionService;
import com.sajarok.global.exception.BusinessException;
import com.sajarok.global.exception.ErrorCode;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** 30번 저장 테스트: 질문 교체와 해시 저장. */
@ExtendWith(MockitoExtension.class)
class RecordQuestionWriterTest {

  @Mock private ProjectRecordRepository projectRecordRepository;
  @Mock private AiQuestionService aiQuestionService;

  @InjectMocks private RecordQuestionWriter recordQuestionWriter;

  private static ProjectRecord record() {
    return ProjectRecord.builder()
        .project(Project.builder().userId(1L).name("사자록").build())
        .status(ProjectRecordStatus.DRAFT)
        .build();
  }

  @Test
  @DisplayName("replace: 기록에 해시를 남기고 기록 소속 질문을 새것으로 바꾼다")
  void replace_savesHashAndQuestions() {
    ProjectRecord record = record();
    List<NewQuestion> questions = List.of(new NewQuestion("근거와 숫자", null, null, "질문"));
    when(projectRecordRepository.findById(77L)).thenReturn(Optional.of(record));
    when(aiQuestionService.replace(QuestionOwner.projectRecord(77L), questions))
        .thenReturn(List.of());

    recordQuestionWriter.replace(77L, questions, "h".repeat(64));

    assertThat(record.getQuestionInputHash()).isEqualTo("h".repeat(64));
  }

  @Test
  @DisplayName("continueWith: 질문은 건드리지 않고 해시만 바꾼다")
  void continueWith_updatesHashOnly() {
    ProjectRecord record = record();
    when(projectRecordRepository.findById(77L)).thenReturn(Optional.of(record));

    recordQuestionWriter.continueWith(77L, "c".repeat(64));

    assertThat(record.getQuestionInputHash()).isEqualTo("c".repeat(64));
    verifyNoInteractions(aiQuestionService);
  }

  @Test
  @DisplayName("AI를 기다리는 사이 기록이 지워졌으면 NOT_FOUND, 질문을 저장하지 않는다")
  void replace_recordDeleted_throwsNotFound() {
    when(projectRecordRepository.findById(77L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> recordQuestionWriter.replace(77L, List.of(), "h".repeat(64)))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.NOT_FOUND);
    verifyNoInteractions(aiQuestionService);
  }
}
