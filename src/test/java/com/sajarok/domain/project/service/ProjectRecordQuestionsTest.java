package com.sajarok.domain.project.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.sajarok.domain.attachment.dto.AttachmentOwner;
import com.sajarok.domain.attachment.dto.AttachmentView;
import com.sajarok.domain.attachment.entity.Attachment;
import com.sajarok.domain.attachment.entity.AttachmentCategory;
import com.sajarok.domain.attachment.entity.AttachmentType;
import com.sajarok.domain.attachment.entity.ExtractStatus;
import com.sajarok.domain.attachment.entity.LinkType;
import com.sajarok.domain.attachment.service.AttachmentService;
import com.sajarok.domain.project.ai.RecordAttachment;
import com.sajarok.domain.project.ai.RecordQuestionResult.Validated;
import com.sajarok.domain.project.entity.Project;
import com.sajarok.domain.project.entity.ProjectRecord;
import com.sajarok.domain.project.entity.ProjectRecordStatus;
import com.sajarok.domain.project.entity.RecordQuestionMode;
import com.sajarok.domain.project.repository.ProjectRecordRepository;
import com.sajarok.domain.project.repository.ProjectRepository;
import com.sajarok.domain.project.service.ProjectRecordService.RecordQuestions;
import com.sajarok.domain.question.dto.NewQuestion;
import com.sajarok.domain.question.dto.QuestionView;
import com.sajarok.domain.question.entity.QuestionOwner;
import com.sajarok.domain.question.entity.QuestionStatus;
import com.sajarok.domain.question.service.AiQuestionService;
import com.sajarok.domain.storycard.service.StoryCardService;
import com.sajarok.global.exception.BusinessException;
import com.sajarok.global.exception.ErrorCode;
import com.sajarok.infra.ai.AiLock;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

/** 30번 기록 AI 질문 서비스 테스트: mode × (질문 없음 / 자료 그대로 / 자료 바뀜), 자료 부족, AI 실패. */
@ExtendWith(MockitoExtension.class)
class ProjectRecordQuestionsTest {

  private static final QuestionOwner OWNER = QuestionOwner.projectRecord(77L);

  @Mock private ProjectRecordRepository projectRecordRepository;
  @Mock private ProjectRepository projectRepository;
  @Mock private StoryCardService storyCardService;
  @Mock private AiQuestionService aiQuestionService;
  @Mock private RecordOrganizer recordOrganizer;
  @Mock private RecordQuestionGenerator recordQuestionGenerator;
  @Mock private RecordQuestionWriter recordQuestionWriter;
  @Spy private AiLock aiLock = new AiLock();
  @Mock private AttachmentService attachmentService;

  @InjectMocks private ProjectRecordService projectRecordService;

  private static final List<NewQuestion> NEW =
      List.of(new NewQuestion("근거와 숫자", null, null, "결과나 반응이 있었나요?"));

  private static ProjectRecord record(String memo) {
    return ProjectRecord.builder()
        .project(Project.builder().userId(1L).name("사자록").build())
        .status(ProjectRecordStatus.DRAFT)
        .memo(memo)
        .build();
  }

  /** 질문을 만든 때와 자료가 같은 기록 */
  private static ProjectRecord unchanged(String memo) {
    ProjectRecord record = record(memo);
    record.recordQuestionInput(MaterialHash.of(record, List.of()));
    return record;
  }

  /** 질문을 만든 뒤 메모가 바뀐 기록 */
  private static ProjectRecord changed() {
    ProjectRecord before = record("11/3 기획 회의");
    ProjectRecord record = record("11/3 기획 회의. 시안 2개 준비");
    record.recordQuestionInput(MaterialHash.of(before, List.of()));
    return record;
  }

  private static QuestionView question(QuestionStatus status) {
    return new QuestionView(950L, 1, "내 역할·과정", null, null, "질문", null, status);
  }

  private void given(ProjectRecord record, List<QuestionView> existing) {
    when(projectRecordRepository.findByIdAndUserId(77L, 1L)).thenReturn(Optional.of(record));
    when(aiQuestionService.findAll(OWNER)).thenReturn(existing);
  }

  @Test
  @DisplayName("30번: 남의 기록이거나 없으면 NOT_FOUND, 질문·AI를 부르지 않는다")
  void notMine_throwsNotFound() {
    when(projectRecordRepository.findByIdAndUserId(77L, 1L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> projectRecordService.createQuestions(1L, 77L, RecordQuestionMode.AUTO))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.NOT_FOUND);
    verifyNoInteractions(aiQuestionService, recordQuestionGenerator, recordQuestionWriter);
  }

  @ParameterizedTest
  @EnumSource(RecordQuestionMode.class)
  @DisplayName("30번: 질문이 없으면 mode와 상관없이 만들고, 질문과 지금 자료 해시를 함께 저장한다 (201)")
  void noQuestions_generatesAndSavesWithHash(RecordQuestionMode mode) {
    ProjectRecord record = record("11/3 기획 회의");
    given(record, List.of());
    when(recordQuestionGenerator.generate(record, List.of())).thenReturn(new Validated(false, NEW));
    when(recordQuestionWriter.replace(77L, NEW, MaterialHash.of(record, List.of())))
        .thenReturn(List.of(question(QuestionStatus.PENDING)));

    RecordQuestions result = projectRecordService.createQuestions(1L, 77L, mode);

    assertThat(result.created()).isTrue();
    assertThat(result.response().insufficientMaterial()).isFalse();
    assertThat(result.response().questions()).hasSize(1);
  }

  @ParameterizedTest
  @EnumSource(
      value = RecordQuestionMode.class,
      names = {"AUTO", "CONTINUE"})
  @DisplayName("30번: 질문이 있고 자료가 그대로면 AUTO·CONTINUE 모두 저장된 질문을 그대로 준다 (200, AI·저장 안 함)")
  void unchanged_returnsSaved(RecordQuestionMode mode) {
    given(unchanged("11/3 기획 회의"), List.of(question(QuestionStatus.ANSWERED)));

    RecordQuestions result = projectRecordService.createQuestions(1L, 77L, mode);

    assertThat(result.created()).isFalse();
    assertThat(result.response().questions()).hasSize(1);
    verifyNoInteractions(recordQuestionGenerator, recordQuestionWriter);
  }

  @Test
  @DisplayName("30번 AUTO: 자료가 바뀌었으면 409 MATERIAL_CHANGED, 메시지와 detail에 쓴 답(ANSWERED) 수를 담는다")
  void changed_auto_throwsMaterialChanged() {
    given(
        changed(),
        List.of(
            question(QuestionStatus.ANSWERED),
            question(QuestionStatus.ANSWERED),
            question(QuestionStatus.SKIPPED)));

    assertThatThrownBy(() -> projectRecordService.createQuestions(1L, 77L, RecordQuestionMode.AUTO))
        .isInstanceOf(BusinessException.class)
        .satisfies(
            e -> {
              BusinessException be = (BusinessException) e;
              assertThat(be.getErrorCode()).isEqualTo(ErrorCode.MATERIAL_CHANGED);
              assertThat(be.getMessage()).isEqualTo("자료가 바뀌었어요. 질문을 새로 만들면 지금까지 쓴 답 2개가 사라져요.");
              assertThat(be.getDetail()).isEqualTo(Map.of("answeredCount", 2L));
            });
    verifyNoInteractions(recordQuestionGenerator, recordQuestionWriter);
  }

  @Test
  @DisplayName("30번: mode를 안 보내면 AUTO로 본다 (자료가 바뀌었으면 409)")
  void nullMode_isAuto() {
    given(changed(), List.of(question(QuestionStatus.PENDING)));

    assertThatThrownBy(() -> projectRecordService.createQuestions(1L, 77L, null))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.MATERIAL_CHANGED);
  }

  @Test
  @DisplayName("30번 CONTINUE: 자료가 바뀌었어도 저장된 질문을 주고, 해시를 지금 자료로 바꾼다 (다음 AUTO에서 다시 안 물음)")
  void changed_continue_keepsQuestionsAndUpdatesHash() {
    ProjectRecord record = changed();
    given(record, List.of(question(QuestionStatus.ANSWERED)));

    RecordQuestions result =
        projectRecordService.createQuestions(1L, 77L, RecordQuestionMode.CONTINUE);

    assertThat(result.created()).isFalse();
    assertThat(result.response().questions()).hasSize(1);
    verify(recordQuestionWriter).continueWith(77L, MaterialHash.of(record, List.of()));
    verifyNoInteractions(recordQuestionGenerator);
  }

  @Test
  @DisplayName("30번 REGENERATE: 질문이 있어도(자료가 그대로여도) 새로 만들어 바꾼다 (201)")
  void regenerate_replaces() {
    ProjectRecord record = unchanged("11/3 기획 회의");
    given(record, List.of(question(QuestionStatus.ANSWERED)));
    when(recordQuestionGenerator.generate(record, List.of())).thenReturn(new Validated(false, NEW));
    when(recordQuestionWriter.replace(77L, NEW, record.getQuestionInputHash()))
        .thenReturn(List.of(question(QuestionStatus.PENDING)));

    RecordQuestions result =
        projectRecordService.createQuestions(1L, 77L, RecordQuestionMode.REGENERATE);

    assertThat(result.created()).isTrue();
  }

  @Test
  @DisplayName("30번: 메모·5칸·첨부가 모두 비면 AI를 부르지 않고 insufficientMaterial로 200 (저장 안 함)")
  void emptyMaterial_insufficientWithoutAi() {
    given(record("  "), List.of());

    RecordQuestions result = projectRecordService.createQuestions(1L, 77L, RecordQuestionMode.AUTO);

    assertThat(result.created()).isFalse();
    assertThat(result.response().insufficientMaterial()).isTrue();
    assertThat(result.response().questions()).isEmpty();
    verifyNoInteractions(recordQuestionGenerator, recordQuestionWriter);
  }

  @Test
  @DisplayName("30번: AI가 자료가 부족하다고 하면 insufficientMaterial로 200, 질문·해시를 저장하지 않는다")
  void aiSaysInsufficient_savesNothing() {
    ProjectRecord record = record("회의함");
    given(record, List.of());
    when(recordQuestionGenerator.generate(record, List.of()))
        .thenReturn(new Validated(true, List.of()));

    RecordQuestions result = projectRecordService.createQuestions(1L, 77L, RecordQuestionMode.AUTO);

    assertThat(result.response().insufficientMaterial()).isTrue();
    verifyNoInteractions(recordQuestionWriter);
  }

  @ParameterizedTest
  @EnumSource(
      value = ErrorCode.class,
      names = {"AI_FAILED", "AI_TIMEOUT"})
  @DisplayName("30번: AI가 실패하거나 시간 초과면 같은 에러, 질문·해시를 저장하지 않는다 (기존 질문 유지)")
  void aiFails_savesNothing(ErrorCode code) {
    given(record("11/3 기획 회의"), List.of());
    when(recordQuestionGenerator.generate(any(), anyList())).thenThrow(new BusinessException(code));

    assertThatThrownBy(() -> projectRecordService.createQuestions(1L, 77L, RecordQuestionMode.AUTO))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(code);
    verify(recordQuestionWriter, never()).replace(anyLong(), anyList(), anyString());
  }

  private static AttachmentView view(Long id, ExtractStatus status) {
    return new AttachmentView(
        id,
        AttachmentType.LINK,
        AttachmentCategory.MEETING_NOTE,
        null,
        "https://notion.so/a",
        LinkType.NOTION,
        null,
        null,
        status);
  }

  @Test
  @DisplayName("30번 첨부: readTexts로 읽기가 끝나길 기다린 뒤, DONE 첨부만 ID마다 읽어 AI에 넘기고(FAILED 건너뜀) 자료 해시에도 넣는다")
  void attachments_readsDoneByIdAndHashesThem() {
    ProjectRecord record = record("11/3 기획 회의");
    given(record, List.of());
    AttachmentOwner owner = AttachmentOwner.projectRecord(77L);
    when(attachmentService.findAll(owner))
        .thenReturn(List.of(view(601L, ExtractStatus.DONE), view(602L, ExtractStatus.FAILED)));
    Attachment done =
        Attachment.createLink(
            1L, AttachmentCategory.MEETING_NOTE, "https://notion.so/a", LinkType.NOTION);
    ReflectionTestUtils.setField(done, "id", 601L);
    ReflectionTestUtils.setField(done, "extractStatus", ExtractStatus.DONE);
    ReflectionTestUtils.setField(done, "extractedText", "홈 화면 시안 A, B 비교");
    when(attachmentService.getOwnedAttachment(1L, 601L)).thenReturn(done);
    List<RecordAttachment> expected =
        List.of(
            new RecordAttachment(
                601L, "LINK", "MEETING_NOTE", "https://notion.so/a", "홈 화면 시안 A, B 비교"));
    when(recordQuestionGenerator.generate(record, expected)).thenReturn(new Validated(false, NEW));
    when(recordQuestionWriter.replace(77L, NEW, MaterialHash.of(record, expected)))
        .thenReturn(List.of(question(QuestionStatus.PENDING)));

    RecordQuestions result = projectRecordService.createQuestions(1L, 77L, RecordQuestionMode.AUTO);

    assertThat(result.created()).isTrue();
    verify(attachmentService).readTexts(owner);
    verify(attachmentService, never()).getOwnedAttachment(1L, 602L);
  }

  @Test
  @DisplayName("30번: 같은 기록에서 AI가 돌고 있으면(30·31번 같은 키 record:{id}) AI_IN_PROGRESS이고 기록도 읽지 않는다")
  void createQuestions_inProgress_throwsAiInProgress() {
    assertThatThrownBy(
            () ->
                aiLock.run(
                    "record:77",
                    () -> projectRecordService.createQuestions(1L, 77L, RecordQuestionMode.AUTO)))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.AI_IN_PROGRESS);
    verifyNoInteractions(projectRecordRepository, recordQuestionGenerator);
  }
}
