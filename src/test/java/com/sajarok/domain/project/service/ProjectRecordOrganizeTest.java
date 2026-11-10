package com.sajarok.domain.project.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.sajarok.domain.project.dto.RecordOrganizeResponse;
import com.sajarok.domain.project.entity.Project;
import com.sajarok.domain.project.entity.ProjectRecord;
import com.sajarok.domain.project.entity.ProjectRecordStatus;
import com.sajarok.domain.project.repository.ProjectRecordRepository;
import com.sajarok.domain.project.repository.ProjectRepository;
import com.sajarok.domain.question.dto.QuestionView;
import com.sajarok.domain.question.entity.QuestionOwner;
import com.sajarok.domain.question.entity.QuestionStatus;
import com.sajarok.domain.question.service.AiQuestionService;
import com.sajarok.domain.storycard.service.StoryCardService;
import com.sajarok.global.exception.BusinessException;
import com.sajarok.global.exception.ErrorCode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** 31번 기록 AI 정리 서비스 테스트. AI(RecordOrganizer)는 가짜. */
@ExtendWith(MockitoExtension.class)
class ProjectRecordOrganizeTest {

  @Mock private ProjectRecordRepository projectRecordRepository;
  @Mock private ProjectRepository projectRepository;
  @Mock private StoryCardService storyCardService;
  @Mock private AiQuestionService aiQuestionService;
  @Mock private RecordOrganizer recordOrganizer;

  @InjectMocks private ProjectRecordService projectRecordService;

  private static ProjectRecord record(String memo, String did) {
    Project project = Project.builder().userId(1L).name("사자록").build();
    return ProjectRecord.builder()
        .project(project)
        .status(ProjectRecordStatus.DRAFT)
        .memo(memo)
        .did(did)
        .build();
  }

  private static QuestionView question(String tag, QuestionStatus status) {
    return new QuestionView(1L, 1, tag, null, null, "질문", "답", status);
  }

  private static Map<String, String> suggestion() {
    Map<String, String> s = new LinkedHashMap<>();
    s.put("did", "AI가 쓴 한 일");
    s.put("concern", "AI가 쓴 고민");
    s.put("decision", null);
    s.put("result", "AI가 쓴 결과");
    s.put("nextStep", null);
    return s;
  }

  @Test
  @DisplayName("31번: 남의 기록이거나 없으면 NOT_FOUND, 질문·AI를 부르지 않는다")
  void organize_notMine_throwsNotFound() {
    when(projectRecordRepository.findByIdAndUserId(77L, 1L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> projectRecordService.organize(1L, 77L))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.NOT_FOUND);
    verifyNoInteractions(aiQuestionService, recordOrganizer);
  }

  @Test
  @DisplayName("31번: 메모·5칸·첨부·답한 질문이 모두 없으면 EMPTY_MATERIAL, AI를 부르지 않는다 (건너뛴 답은 자료로 안 셈)")
  void organize_noMaterial_throwsEmptyMaterial() {
    when(projectRecordRepository.findByIdAndUserId(77L, 1L))
        .thenReturn(Optional.of(record(" ", null)));
    when(aiQuestionService.findAll(QuestionOwner.projectRecord(77L)))
        .thenReturn(List.of(question("내 역할", QuestionStatus.SKIPPED)));

    assertThatThrownBy(() -> projectRecordService.organize(1L, 77L))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.EMPTY_MATERIAL);
    verifyNoInteractions(recordOrganizer);
  }

  @Test
  @DisplayName(
      "31번: 답한(ANSWERED) 질문만 AI에 넘기고, 사용자가 이미 쓴 칸(did)은 AI 제안 대신 원문을, 빈 칸은 null과 blanks로 준다")
  @SuppressWarnings("unchecked")
  void organize_keepsWrittenFieldsAndPassesOnlyAnswered() {
    ProjectRecord record = record("11/3 기획 회의", "내가 쓴 한 일");
    when(projectRecordRepository.findByIdAndUserId(77L, 1L)).thenReturn(Optional.of(record));
    QuestionView answered = question("내 역할", QuestionStatus.ANSWERED);
    when(aiQuestionService.findAll(QuestionOwner.projectRecord(77L)))
        .thenReturn(List.of(answered, question("과정", QuestionStatus.PENDING)));
    when(recordOrganizer.organize(eq(record), anyList(), anyList())).thenReturn(suggestion());

    RecordOrganizeResponse response = projectRecordService.organize(1L, 77L);

    ArgumentCaptor<List<QuestionView>> captor = ArgumentCaptor.forClass(List.class);
    verify(recordOrganizer).organize(eq(record), captor.capture(), anyList());
    assertThat(captor.getValue()).containsExactly(answered);
    assertThat(response.did()).isEqualTo("내가 쓴 한 일");
    assertThat(response.concern()).isEqualTo("AI가 쓴 고민");
    assertThat(response.decision()).isNull();
    assertThat(response.result()).isEqualTo("AI가 쓴 결과");
    assertThat(response.blanks()).containsExactly("decision", "nextStep");
  }

  @Test
  @DisplayName("31번: 메모가 없어도 답한 질문이 있으면 자료로 보고 정리한다")
  void organize_answersOnly_isMaterial() {
    ProjectRecord record = record(null, null);
    when(projectRecordRepository.findByIdAndUserId(77L, 1L)).thenReturn(Optional.of(record));
    when(aiQuestionService.findAll(QuestionOwner.projectRecord(77L)))
        .thenReturn(List.of(question("내 역할", QuestionStatus.ANSWERED)));
    when(recordOrganizer.organize(eq(record), anyList(), anyList())).thenReturn(suggestion());

    assertThat(projectRecordService.organize(1L, 77L).did()).isEqualTo("AI가 쓴 한 일");
  }

  @ParameterizedTest
  @EnumSource(
      value = ErrorCode.class,
      names = {"AI_FAILED", "AI_TIMEOUT"})
  @DisplayName("31번: AI가 실패하거나 시간 초과면 같은 에러를 돌려준다")
  void organize_aiFails_rethrows(ErrorCode code) {
    when(projectRecordRepository.findByIdAndUserId(77L, 1L))
        .thenReturn(Optional.of(record("메모", null)));
    when(aiQuestionService.findAll(QuestionOwner.projectRecord(77L))).thenReturn(List.of());
    when(recordOrganizer.organize(any(), anyList(), anyList()))
        .thenThrow(new BusinessException(code));

    assertThatThrownBy(() -> projectRecordService.organize(1L, 77L))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(code);
  }
}
