package com.sajarok.domain.retrospective.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
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
import com.sajarok.domain.attachment.service.AttachmentService;
import com.sajarok.domain.question.dto.NewQuestion;
import com.sajarok.domain.question.dto.QuestionView;
import com.sajarok.domain.question.entity.QuestionOwner;
import com.sajarok.domain.question.entity.QuestionStatus;
import com.sajarok.domain.question.service.AiQuestionService;
import com.sajarok.domain.retrospective.ai.RetroAttachment;
import com.sajarok.domain.retrospective.ai.RetroQuestionResult.Validated;
import com.sajarok.domain.retrospective.dto.RetroQuestionMode;
import com.sajarok.domain.retrospective.dto.RetrospectiveReviewResponse;
import com.sajarok.domain.retrospective.entity.Retrospective;
import com.sajarok.domain.retrospective.entity.RetrospectiveType;
import com.sajarok.domain.retrospective.repository.RetrospectiveRepository;
import com.sajarok.domain.retrospective.service.RetrospectiveAiService.QuestionsResult;
import com.sajarok.domain.storycard.dto.RetroCardDraft;
import com.sajarok.domain.storycard.dto.StoryCardDetailResponse;
import com.sajarok.domain.storycard.entity.StoryCardStatus;
import com.sajarok.domain.storycard.entity.StoryCardType;
import com.sajarok.domain.storycard.service.StoryCardService;
import com.sajarok.domain.user.entity.Part;
import com.sajarok.domain.user.entity.User;
import com.sajarok.domain.user.repository.UserRepository;
import com.sajarok.domain.week.service.WeekService;
import com.sajarok.global.exception.BusinessException;
import com.sajarok.global.exception.ErrorCode;
import com.sajarok.infra.ai.AiLock;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 서비스 테스트: 17번 회고 AI 질문의 mode 규칙(AUTO·CONTINUE·REGENERATE), 자료 해시, 자료 부족, 첨부 읽기, 잠금과 19번 회고록·카드
 * 초안(답한 질문만 쓰기, 자료 없음, 같은 잠금)을 확인한다.
 *
 * <p>AI는 Generator를 가짜(@Mock)로 바꿔서 부르지 않는다. 잠금(AiLock)은 진짜(@Spy)를 써서 동시 호출을 실제로 막는지 본다.
 */
@ExtendWith(MockitoExtension.class)
class RetrospectiveAiServiceTest {

  private static final Long ME = 1L;
  private static final Long RETRO = 101L;
  private static final QuestionOwner QUESTIONS_OF_101 = QuestionOwner.retrospective(RETRO);
  private static final AttachmentOwner ATTACHMENTS_OF_101 = AttachmentOwner.retrospective(RETRO);

  @Mock private RetrospectiveRepository retrospectiveRepository;
  @Mock private UserRepository userRepository;
  @Mock private AttachmentService attachmentService;
  @Mock private AiQuestionService aiQuestionService;
  @Mock private RetroQuestionGenerator retroQuestionGenerator;
  @Mock private RetroQuestionWriter retroQuestionWriter;
  @Mock private StoryCardService storyCardService;
  @Mock private RetroReviewGenerator retroReviewGenerator;
  @Mock private WeekService weekService;
  @Spy private AiLock aiLock = new AiLock();

  @InjectMocks private RetrospectiveAiService retrospectiveAiService;

  /** 내 세션 회고(id 101). 메모를 적어 둔다 */
  private Retrospective myRetroWithMemo(String memo) {
    Retrospective r = Retrospective.start(ME, 10L, RetrospectiveType.SESSION);
    ReflectionTestUtils.setField(r, "id", RETRO);
    ReflectionTestUtils.setField(r, "memo", memo);
    when(retrospectiveRepository.findByIdAndUserId(RETRO, ME)).thenReturn(Optional.of(r));
    return r;
  }

  private static QuestionView question(long id, QuestionStatus status) {
    return new QuestionView(id, (int) (id - 900), "근거와 숫자", null, null, "숫자가 있나요?", null, status);
  }

  private static final List<NewQuestion> NEW_QUESTIONS =
      List.of(new NewQuestion("근거와 숫자", null, null, "쿼리 수가 얼마나 줄었나요?"));

  private static final List<QuestionView> SAVED = List.of(question(901L, QuestionStatus.PENDING));

  private static AttachmentView view(long id, ExtractStatus status) {
    return new AttachmentView(
        id,
        AttachmentType.FILE,
        AttachmentCategory.SESSION_PDF,
        "7주차.pdf",
        null,
        null,
        null,
        null,
        status);
  }

  /** 추출이 끝난 세션 PDF 첨부 엔티티 (getter만 필요해서 mock으로 만든다) */
  private static Attachment donePdf(long id, String text) {
    Attachment a = mock(Attachment.class);
    when(a.getId()).thenReturn(id);
    when(a.getType()).thenReturn(AttachmentType.FILE);
    when(a.getCategory()).thenReturn(AttachmentCategory.SESSION_PDF);
    when(a.getName()).thenReturn("7주차.pdf");
    when(a.getExtractedText()).thenReturn(text);
    return a;
  }

  @Test
  @DisplayName("17번: 질문이 없으면 AI로 만들어 저장하고 created=true(201). 자료 해시(64자)도 같이 저장한다")
  void create_new() {
    Retrospective r = myRetroWithMemo("N+1 문제를 fetch join으로 해결");
    when(retroQuestionGenerator.generate(r, null, List.of()))
        .thenReturn(new Validated(false, NEW_QUESTIONS));
    when(retroQuestionWriter.replace(eq(RETRO), eq(NEW_QUESTIONS), anyString())).thenReturn(SAVED);

    QuestionsResult result = retrospectiveAiService.createQuestions(ME, RETRO, null);

    assertThat(result.created()).isTrue();
    assertThat(result.response().insufficientMaterial()).isFalse();
    assertThat(result.response().questions()).isEqualTo(SAVED);
    verify(retroQuestionWriter)
        .replace(eq(RETRO), eq(NEW_QUESTIONS), argThat(hash -> hash.length() == 64));
  }

  @Test
  @DisplayName("17번: 회원 파트를 찾아 AI에 넘긴다 (파트별 질문 세트)")
  void create_passesPart() {
    Retrospective r = myRetroWithMemo("메모");
    User me = mock(User.class);
    when(me.getPart()).thenReturn(Part.BACKEND);
    when(userRepository.findById(ME)).thenReturn(Optional.of(me));
    when(retroQuestionGenerator.generate(r, Part.BACKEND, List.of()))
        .thenReturn(new Validated(false, NEW_QUESTIONS));
    when(retroQuestionWriter.replace(eq(RETRO), eq(NEW_QUESTIONS), anyString())).thenReturn(SAVED);

    retrospectiveAiService.createQuestions(ME, RETRO, RetroQuestionMode.AUTO);

    verify(retroQuestionGenerator).generate(r, Part.BACKEND, List.of());
  }

  @Test
  @DisplayName("17번 AUTO: 질문이 있고 자료가 그대로면 AI를 부르지 않고 저장된 질문을 준다 (200)")
  void auto_sameMaterial_returnsExisting() {
    Retrospective r = myRetroWithMemo("메모");
    r.recordQuestionInput(RetroMaterialHash.of(r, List.of()));
    List<QuestionView> existing = List.of(question(901L, QuestionStatus.ANSWERED));
    when(aiQuestionService.findAll(QUESTIONS_OF_101)).thenReturn(existing);

    QuestionsResult result = retrospectiveAiService.createQuestions(ME, RETRO, null);

    assertThat(result.created()).isFalse();
    assertThat(result.response().questions()).isEqualTo(existing);
    verifyNoInteractions(retroQuestionGenerator, retroQuestionWriter);
  }

  @Test
  @DisplayName("17번 AUTO: 질문을 만든 뒤 자료가 바뀌었으면 409 MATERIAL_CHANGED와 답한 개수(answeredCount)를 준다")
  void auto_changedMaterial_conflict() {
    Retrospective r = myRetroWithMemo("처음 메모");
    r.recordQuestionInput(RetroMaterialHash.of(r, List.of()));
    ReflectionTestUtils.setField(r, "memo", "고친 메모");
    when(aiQuestionService.findAll(QUESTIONS_OF_101))
        .thenReturn(
            List.of(
                question(901L, QuestionStatus.ANSWERED),
                question(902L, QuestionStatus.ANSWERED),
                question(903L, QuestionStatus.PENDING)));

    assertThatThrownBy(() -> retrospectiveAiService.createQuestions(ME, RETRO, null))
        .isInstanceOf(BusinessException.class)
        .satisfies(
            e -> {
              BusinessException be = (BusinessException) e;
              assertThat(be.getErrorCode()).isEqualTo(ErrorCode.MATERIAL_CHANGED);
              assertThat(be.getDetail()).isEqualTo(Map.of("answeredCount", 2L));
              assertThat(be.getMessage()).contains("답 2개");
            });
    verifyNoInteractions(retroQuestionGenerator, retroQuestionWriter);
  }

  @Test
  @DisplayName("17번 CONTINUE: 자료가 바뀌어도 기존 질문을 이어 쓰고, 해시만 지금 자료로 바꾼다 (200)")
  void continue_changedMaterial_keepsQuestionsAndUpdatesHash() {
    Retrospective r = myRetroWithMemo("처음 메모");
    r.recordQuestionInput(RetroMaterialHash.of(r, List.of()));
    ReflectionTestUtils.setField(r, "memo", "고친 메모");
    String newHash = RetroMaterialHash.of(r, List.of());
    List<QuestionView> existing = List.of(question(901L, QuestionStatus.ANSWERED));
    when(aiQuestionService.findAll(QUESTIONS_OF_101)).thenReturn(existing);

    QuestionsResult result =
        retrospectiveAiService.createQuestions(ME, RETRO, RetroQuestionMode.CONTINUE);

    assertThat(result.created()).isFalse();
    assertThat(result.response().questions()).isEqualTo(existing);
    verify(retroQuestionWriter).continueWith(RETRO, newHash);
    verify(retroQuestionWriter, never()).replace(any(), any(), any());
    verifyNoInteractions(retroQuestionGenerator);
  }

  @Test
  @DisplayName("17번 REGENERATE: 질문이 있어도 AI로 새로 만든다 (201)")
  void regenerate_replacesExisting() {
    Retrospective r = myRetroWithMemo("메모");
    r.recordQuestionInput(RetroMaterialHash.of(r, List.of()));
    when(aiQuestionService.findAll(QUESTIONS_OF_101))
        .thenReturn(List.of(question(901L, QuestionStatus.ANSWERED)));
    when(retroQuestionGenerator.generate(r, null, List.of()))
        .thenReturn(new Validated(false, NEW_QUESTIONS));
    when(retroQuestionWriter.replace(eq(RETRO), eq(NEW_QUESTIONS), anyString())).thenReturn(SAVED);

    QuestionsResult result =
        retrospectiveAiService.createQuestions(ME, RETRO, RetroQuestionMode.REGENERATE);

    assertThat(result.created()).isTrue();
    assertThat(result.response().questions()).isEqualTo(SAVED);
  }

  @Test
  @DisplayName("17번: 회고 자료도 첨부도 없으면 AI를 부르지 않고 insufficientMaterial=true (200)")
  void noMaterial_insufficientWithoutAi() {
    myRetroWithMemo("   ");

    QuestionsResult result = retrospectiveAiService.createQuestions(ME, RETRO, null);

    assertThat(result.created()).isFalse();
    assertThat(result.response().insufficientMaterial()).isTrue();
    assertThat(result.response().questions()).isEmpty();
    verifyNoInteractions(retroQuestionGenerator, retroQuestionWriter);
  }

  @Test
  @DisplayName("17번: 메모가 비어도 읽은(DONE) 첨부가 있으면 AI를 부른다. 읽지 못한(FAILED) 첨부는 빼고 넘긴다")
  void attachmentsOnly_usesDoneAttachments() {
    Retrospective r = myRetroWithMemo(null);
    when(attachmentService.findAll(ATTACHMENTS_OF_101))
        .thenReturn(List.of(view(601L, ExtractStatus.DONE), view(602L, ExtractStatus.FAILED)));
    Attachment pdf = donePdf(601L, "지연 로딩과 N+1 문제");
    when(attachmentService.getOwnedAttachment(ME, 601L)).thenReturn(pdf);
    List<RetroAttachment> expected =
        List.of(new RetroAttachment(601L, "FILE", "SESSION_PDF", "7주차.pdf", "지연 로딩과 N+1 문제"));
    when(retroQuestionGenerator.generate(r, null, expected))
        .thenReturn(new Validated(false, NEW_QUESTIONS));
    when(retroQuestionWriter.replace(eq(RETRO), eq(NEW_QUESTIONS), anyString())).thenReturn(SAVED);

    QuestionsResult result = retrospectiveAiService.createQuestions(ME, RETRO, null);

    assertThat(result.created()).isTrue();
    verify(attachmentService).readTexts(ATTACHMENTS_OF_101);
    verify(attachmentService, never()).getOwnedAttachment(ME, 602L);
  }

  @Test
  @DisplayName("17번: 첨부를 아직 읽는 중이면 409 ATTACHMENT_PENDING이 그대로 나가고 AI는 부르지 않는다")
  void attachmentPending_conflict() {
    myRetroWithMemo("메모");
    when(attachmentService.readTexts(ATTACHMENTS_OF_101))
        .thenThrow(new BusinessException(ErrorCode.ATTACHMENT_PENDING));

    assertThatThrownBy(() -> retrospectiveAiService.createQuestions(ME, RETRO, null))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.ATTACHMENT_PENDING);
    verifyNoInteractions(retroQuestionGenerator, retroQuestionWriter);
  }

  @Test
  @DisplayName("17번: AI가 자료가 부족하다고 하면 저장하지 않고 insufficientMaterial=true (200)")
  void aiSaysInsufficient_notSaved() {
    Retrospective r = myRetroWithMemo("실습함");
    when(retroQuestionGenerator.generate(r, null, List.of()))
        .thenReturn(new Validated(true, List.of()));

    QuestionsResult result = retrospectiveAiService.createQuestions(ME, RETRO, null);

    assertThat(result.created()).isFalse();
    assertThat(result.response().insufficientMaterial()).isTrue();
    verifyNoInteractions(retroQuestionWriter);
  }

  @Test
  @DisplayName("17번: 없거나 남의 회고면 NOT_FOUND이고, 잠금·첨부·AI 모두 건드리지 않는다")
  void notMine_notFound() {
    when(retrospectiveRepository.findByIdAndUserId(999L, ME)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> retrospectiveAiService.createQuestions(ME, 999L, null))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.NOT_FOUND);
    verify(aiLock, never()).run(anyString(), any());
    verifyNoInteractions(attachmentService, retroQuestionGenerator, retroQuestionWriter);
  }

  @Test
  @DisplayName("17번: AI가 도는 동안 같은 회고로 또 부르면 409 AI_IN_PROGRESS (버튼 두 번 누르기 방지)")
  void sameRetroWhileRunning_aiInProgress() {
    Retrospective r = myRetroWithMemo("메모");
    when(retroQuestionGenerator.generate(r, null, List.of()))
        .thenAnswer(
            invocation -> {
              // AI를 기다리는 사이에 두 번째 요청이 들어온 상황
              assertThatThrownBy(() -> retrospectiveAiService.createQuestions(ME, RETRO, null))
                  .isInstanceOf(BusinessException.class)
                  .extracting("errorCode")
                  .isEqualTo(ErrorCode.AI_IN_PROGRESS);
              return new Validated(false, NEW_QUESTIONS);
            });
    when(retroQuestionWriter.replace(eq(RETRO), eq(NEW_QUESTIONS), anyString())).thenReturn(SAVED);

    QuestionsResult first = retrospectiveAiService.createQuestions(ME, RETRO, null);

    assertThat(first.created()).isTrue();
  }

  @Test
  @DisplayName("17번: AI가 실패(AI_FAILED)해도 잠금은 풀려서 다시 시도할 수 있다")
  void aiFailed_releasesLock() {
    Retrospective r = myRetroWithMemo("메모");
    when(retroQuestionGenerator.generate(r, null, List.of()))
        .thenThrow(new BusinessException(ErrorCode.AI_FAILED))
        .thenReturn(new Validated(false, NEW_QUESTIONS));
    when(retroQuestionWriter.replace(eq(RETRO), eq(NEW_QUESTIONS), anyString())).thenReturn(SAVED);

    assertThatThrownBy(() -> retrospectiveAiService.createQuestions(ME, RETRO, null))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.AI_FAILED);
    QuestionsResult retry = retrospectiveAiService.createQuestions(ME, RETRO, null);

    assertThat(retry.created()).isTrue();
  }

  // ---------- 19번 ----------

  private static RetroCardDraft cardDraft() {
    return new RetroCardDraft(
        ME,
        RETRO,
        10L,
        StoryCardType.SESSION,
        "N+1 해결",
        "상황",
        "행동",
        null,
        "결과",
        null,
        "# 세션 회고\n\n## 실습",
        "{}");
  }

  private static StoryCardDetailResponse savedCard() {
    return new StoryCardDetailResponse(
        301L,
        StoryCardType.SESSION,
        StoryCardStatus.DRAFT,
        null,
        null,
        null,
        "N+1 해결",
        "상황",
        "행동",
        null,
        "결과",
        null,
        List.of("reason", "learned"),
        List.of(),
        "# 세션 회고\n\n## 실습",
        "{}",
        List.of(),
        null);
  }

  @Test
  @DisplayName("19번: 답한(ANSWERED) 질문만 AI에 넘기고, 만든 초안을 카드로 저장해 { card }로 준다")
  void review_usesAnsweredOnlyAndSavesCard() {
    Retrospective r = myRetroWithMemo("N+1 문제를 fetch join으로 해결");
    QuestionView answered = question(901L, QuestionStatus.ANSWERED);
    when(aiQuestionService.findAll(QUESTIONS_OF_101))
        .thenReturn(List.of(answered, question(902L, QuestionStatus.PENDING)));
    RetroCardDraft draft = cardDraft();
    when(retroReviewGenerator.generate(r, null, null, List.of(), List.of(answered)))
        .thenReturn(draft);
    when(storyCardService.saveRetrospectiveDraft(draft)).thenReturn(savedCard());

    RetrospectiveReviewResponse response = retrospectiveAiService.review(ME, RETRO);

    assertThat(response.card().cardId()).isEqualTo(301L);
    assertThat(response.card().status()).isEqualTo(StoryCardStatus.DRAFT);
  }

  @Test
  @DisplayName("19번: 메모가 비어도 답한 질문이 있으면 초안을 만든다")
  void review_answersOnly_proceeds() {
    Retrospective r = myRetroWithMemo(null);
    QuestionView answered = question(901L, QuestionStatus.ANSWERED);
    when(aiQuestionService.findAll(QUESTIONS_OF_101)).thenReturn(List.of(answered));
    RetroCardDraft draft = cardDraft();
    when(retroReviewGenerator.generate(r, null, null, List.of(), List.of(answered)))
        .thenReturn(draft);
    when(storyCardService.saveRetrospectiveDraft(draft)).thenReturn(savedCard());

    assertThat(retrospectiveAiService.review(ME, RETRO).card()).isNotNull();
  }

  @Test
  @DisplayName("19번: 회고 자료·첨부·답한 질문이 모두 없으면 400 EMPTY_MATERIAL이고, AI도 카드도 건드리지 않는다")
  void review_noMaterial_emptyMaterial() {
    myRetroWithMemo(" ");
    when(aiQuestionService.findAll(QUESTIONS_OF_101))
        .thenReturn(List.of(question(901L, QuestionStatus.PENDING)));

    assertThatThrownBy(() -> retrospectiveAiService.review(ME, RETRO))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.EMPTY_MATERIAL);
    verifyNoInteractions(retroReviewGenerator, storyCardService);
  }

  @Test
  @DisplayName("19번: 없거나 남의 회고면 NOT_FOUND이고, 잠금을 잡지 않는다")
  void review_notMine_notFound() {
    when(retrospectiveRepository.findByIdAndUserId(999L, ME)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> retrospectiveAiService.review(ME, 999L))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.NOT_FOUND);
    verify(aiLock, never()).run(anyString(), any());
    verifyNoInteractions(retroReviewGenerator, storyCardService);
  }

  @Test
  @DisplayName("19번: 17번 질문 만들기가 도는 동안 초안을 부르면 409 AI_IN_PROGRESS (같은 잠금 키)")
  void review_whileQuestionsRunning_aiInProgress() {
    Retrospective r = myRetroWithMemo("메모");
    when(retroQuestionGenerator.generate(r, null, List.of()))
        .thenAnswer(
            invocation -> {
              assertThatThrownBy(() -> retrospectiveAiService.review(ME, RETRO))
                  .isInstanceOf(BusinessException.class)
                  .extracting("errorCode")
                  .isEqualTo(ErrorCode.AI_IN_PROGRESS);
              return new Validated(false, NEW_QUESTIONS);
            });
    when(retroQuestionWriter.replace(eq(RETRO), eq(NEW_QUESTIONS), anyString())).thenReturn(SAVED);

    retrospectiveAiService.createQuestions(ME, RETRO, null);

    verifyNoInteractions(retroReviewGenerator);
  }

  @Test
  @DisplayName("19번: 회고록 제목에 쓰도록 주차 번호를 찾아 Generator에 넘긴다")
  void review_passesWeekNumber() {
    Retrospective r = myRetroWithMemo("메모");
    when(weekService.findWeekNumbers(List.of(10L))).thenReturn(Map.of(10L, 10));
    RetroCardDraft draft = cardDraft();
    when(retroReviewGenerator.generate(r, 10, null, List.of(), List.of())).thenReturn(draft);
    when(storyCardService.saveRetrospectiveDraft(draft)).thenReturn(savedCard());

    retrospectiveAiService.review(ME, RETRO);

    verify(retroReviewGenerator).generate(r, 10, null, List.of(), List.of());
  }
}
