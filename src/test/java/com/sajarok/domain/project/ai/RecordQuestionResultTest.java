package com.sajarok.domain.project.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.groups.Tuple.tuple;

import com.sajarok.domain.project.ai.RecordQuestionResult.Question;
import com.sajarok.domain.project.ai.RecordQuestionResult.Validated;
import com.sajarok.domain.project.entity.Project;
import com.sajarok.domain.project.entity.ProjectRecord;
import com.sajarok.domain.project.entity.ProjectRecordStatus;
import com.sajarok.domain.question.dto.NewQuestion;
import com.sajarok.global.exception.BusinessException;
import com.sajarok.global.exception.ErrorCode;
import com.sajarok.infra.ai.AttachmentBudget;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 30번 AI 응답 검증과 프롬프트 조립 테스트. */
class RecordQuestionResultTest {

  private static final String MEMO = "11/3 기획 회의. 홈 화면 구성 논의. 나는 시안 2개 준비";
  private static final Map<Long, String> ATTACHMENTS = Map.of(601L, "홈 화면에 무엇을\n먼저 보여 줄지   논의함.");

  private static Question q(String tag, String quote, Long source) {
    return new Question(tag, quote, source, "질문");
  }

  private static void assertAiFailed(RecordQuestionResult result) {
    assertThatThrownBy(() -> result.validate(MEMO, ATTACHMENTS))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.AI_FAILED);
  }

  @Test
  @DisplayName("30번 검증: 원문에 있는 quote는 출처와 함께 남기고(공백 차이는 무시), 같은 태그가 겹쳐도 된다")
  void validate_keepsQuotedEvidence() {
    Validated v =
        new RecordQuestionResult(
                false,
                List.of(
                    q("내 역할·과정", "나는 시안 2개 준비", null),
                    q("내 역할·과정", "홈 화면에 무엇을 먼저 보여 줄지 논의함", 601L)))
            .validate(MEMO, ATTACHMENTS);

    assertThat(v.insufficientMaterial()).isFalse();
    assertThat(v.questions())
        .extracting(NewQuestion::quote, NewQuestion::sourceAttachmentId)
        .containsExactly(tuple("나는 시안 2개 준비", null), tuple("홈 화면에 무엇을 먼저 보여 줄지 논의함", 601L));
  }

  @Test
  @DisplayName("30번 검증: 원문에 없는 quote는 quote·출처를 null로 바꾸고 질문은 살린다 (명세: 원문에 없으면 null)")
  void validate_dropsUnquotedEvidence() {
    Validated v =
        new RecordQuestionResult(false, List.of(q("근거와 숫자", "클릭률 30% 상승", 601L)))
            .validate(MEMO, ATTACHMENTS);

    assertThat(v.questions().get(0).quote()).isNull();
    assertThat(v.questions().get(0).sourceAttachmentId()).isNull();
    assertThat(v.questions().get(0).content()).isEqualTo("질문");
  }

  @Test
  @DisplayName("30번 검증: quote에 '메모: ' 같은 칸 이름이 붙어 와도 떼고 출처로 남긴다")
  void validate_stripsFieldLabel() {
    Validated v =
        new RecordQuestionResult(false, List.of(q("내 역할·과정", "메모: 나는 시안 2개 준비", null)))
            .validate(MEMO, ATTACHMENTS);

    assertThat(v.questions().get(0).quote()).isEqualTo("나는 시안 2개 준비");
  }

  @Test
  @DisplayName("30번 검증: 자료 부족(true)이면 빈 결과로 통과하고, 질문이 같이 와도 자료 부족으로 본다")
  void validate_insufficient() {
    assertThat(
            new RecordQuestionResult(true, List.of())
                .validate(MEMO, ATTACHMENTS)
                .insufficientMaterial())
        .isTrue();
    Validated withQuestions =
        new RecordQuestionResult(true, List.of(q("근거와 숫자", null, null)))
            .validate(MEMO, ATTACHMENTS);
    assertThat(withQuestions.insufficientMaterial()).isTrue();
    assertThat(withQuestions.questions()).isEmpty();
  }

  @Test
  @DisplayName("30번 검증: 쓸 수 있는 질문이 하나도 없으면(0개·목록 밖 태그만·빈 질문만) AI_FAILED")
  void validate_nothingUsable() {
    assertAiFailed(new RecordQuestionResult(false, List.of()));
    assertAiFailed(new RecordQuestionResult(false, null));
    assertAiFailed(new RecordQuestionResult(false, List.of(q("느낀 점", null, null))));
    assertAiFailed(
        new RecordQuestionResult(false, List.of(new Question("근거와 숫자", null, null, " "))));
  }

  @Test
  @DisplayName("30번 검증: 목록 밖 태그·빈 질문은 그 질문만 버리고 나머지는 살린다 (전에는 전체 실패)")
  void validate_dropsOnlyBadQuestions() {
    Validated v =
        new RecordQuestionResult(
                false,
                List.of(
                    q("느낀 점", null, null),
                    new Question("근거와 숫자", null, null, " "),
                    q("선택의 이유", null, null)))
            .validate(MEMO, ATTACHMENTS);

    assertThat(v.questions()).extracting(NewQuestion::tag).containsExactly("선택의 이유");
  }

  @Test
  @DisplayName("30번 검증: 태그가 띄어쓰기·가운뎃점만 다르면 목록의 태그로 맞춘다")
  void validate_normalizesTag() {
    Validated v =
        new RecordQuestionResult(
                false,
                List.of(
                    q("내 역할 · 과정", null, null), q("내 역할ㆍ과정", null, null), q("근거와숫자", null, null)))
            .validate(MEMO, ATTACHMENTS);

    assertThat(v.questions())
        .extracting(NewQuestion::tag)
        .containsExactly("내 역할·과정", "내 역할·과정", "근거와 숫자");
  }

  @Test
  @DisplayName("30번 검증: 질문이 4개를 넘으면 앞에서 4개만 쓴다 (전에는 전체 실패)")
  void validate_limitsToFour() {
    Validated v =
        new RecordQuestionResult(false, Collections.nCopies(5, q("근거와 숫자", null, null)))
            .validate(MEMO, ATTACHMENTS);

    assertThat(v.questions()).hasSize(RecordQuestionResult.MAX_QUESTIONS);
  }

  @Test
  @DisplayName("30번 검증: 넘겨주지 않은 첨부 번호여도 질문은 살리고, 출처는 quote가 실제로 있는 곳으로 바로잡는다 (어디에도 없으면 null)")
  void validate_unknownAttachment_keepsQuestion() {
    Validated v =
        new RecordQuestionResult(
                false, List.of(q("선택의 이유", "시안", 999L), q("근거와 숫자", "지어낸 문장", 999L)))
            .validate(MEMO, ATTACHMENTS);

    assertThat(v.questions())
        .extracting(NewQuestion::tag, NewQuestion::quote, NewQuestion::sourceAttachmentId)
        .containsExactly(tuple("선택의 이유", "시안", null), tuple("근거와 숫자", null, null));
  }

  @Test
  @DisplayName("30번 검증: AI가 출처를 비웠어도(메모라고 했어도) quote가 첨부에 있으면 그 첨부를 출처로 저장한다")
  void validate_correctsSourceToAttachment() {
    Validated v =
        new RecordQuestionResult(false, List.of(q("내 역할·과정", "먼저 보여 줄지 논의함", null)))
            .validate(MEMO, ATTACHMENTS);

    assertThat(v.questions())
        .extracting(NewQuestion::quote, NewQuestion::sourceAttachmentId)
        .containsExactly(tuple("먼저 보여 줄지 논의함", 601L));
  }

  @Test
  @DisplayName("프롬프트: 태그 목록·메모·기록·첨부(번호·종류·이름·본문)·빈칸이 이름표 구역으로 들어가고, system에 빈칸마다 질문하는 규칙이 있다")
  void questionsUser_containsSections() {
    ProjectRecord record =
        ProjectRecord.builder()
            .project(Project.builder().userId(1L).name("사자록").build())
            .status(ProjectRecordStatus.DRAFT)
            .memo(MEMO)
            .build();

    String user =
        RecordPrompts.questionsUser(
            record, List.of(new RecordAttachment(601L, "FILE", "MEETING_NOTE", "회의록.pdf", "본문")));

    assertThat(user)
        .contains(
            "[태그 목록]\n내 역할·과정 / 선택의 이유 / 근거와 숫자",
            "[메모·기록]\n메모: " + MEMO,
            "[첨부]\n(601) FILE / MEETING_NOTE / 회의록.pdf: 본문",
            "[빈칸]\n한 일 / 고민·해결 / 선택과 이유 / 결과·반응 / 다음");
    assertThat(RecordPrompts.QUESTIONS_SYSTEM)
        .contains(
            "먼저 물을 거리가 있는지 본다",
            "질문은 최대 4개",
            "[빈칸]에 적힌 칸마다 질문을 하나씩",
            "같은 태그를 여러 질문에 써도 된다",
            "결과·반응 → 근거와 숫자",
            "기타(OTHER)",
            "30%",
            "태그와 질문이 맞지 않는다");
  }

  @Test
  @DisplayName("빈칸: 채운 칸은 빼고 순서대로, 공백뿐인 칸은 빈칸으로 보고, 메모는 칸이 아니라 넣지 않으며, 다 채웠으면 없음")
  void blanks_listsEmptyFieldsOnly() {
    Project project = Project.builder().userId(1L).name("사자록").build();
    ProjectRecord partial =
        ProjectRecord.builder()
            .project(project)
            .status(ProjectRecordStatus.DRAFT)
            .memo(MEMO)
            .did("시안 2개 준비")
            .concern("  ")
            .result("3명 모두 B안 선호")
            .build();
    ProjectRecord full =
        ProjectRecord.builder()
            .project(project)
            .status(ProjectRecordStatus.DRAFT)
            .did("a")
            .concern("b")
            .decision("c")
            .result("d")
            .nextStep("e")
            .build();

    assertThat(RecordPrompts.blanks(partial)).isEqualTo("고민·해결 / 선택과 이유 / 다음");
    assertThat(RecordPrompts.blanks(full)).isEqualTo("없음");
  }

  @Test
  @DisplayName("30번 프롬프트: 첨부 글자는 다 합쳐 4만 자까지만 넣는다")
  void questionsUser_cutsLongAttachment() {
    ProjectRecord record =
        ProjectRecord.builder()
            .project(Project.builder().userId(1L).name("사자록").build())
            .status(ProjectRecordStatus.DRAFT)
            .build();
    String kept = "가".repeat(AttachmentBudget.TOTAL_CHARS);

    String user =
        RecordPrompts.questionsUser(
            record,
            List.of(new RecordAttachment(601L, "FILE", "MEETING_NOTE", "회의록.pdf", kept + "뒷부분")));

    assertThat(user).contains(": " + kept + "\n").doesNotContain("뒷부분");
  }
}
