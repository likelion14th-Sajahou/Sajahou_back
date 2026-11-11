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
  @DisplayName("30번 검증: 자료 부족(true)이면 질문이 비어야 하고 빈 결과로 통과한다")
  void validate_insufficient() {
    assertThat(
            new RecordQuestionResult(true, List.of())
                .validate(MEMO, ATTACHMENTS)
                .insufficientMaterial())
        .isTrue();
    assertAiFailed(new RecordQuestionResult(true, List.of(q("근거와 숫자", null, null))));
  }

  @Test
  @DisplayName("30번 검증: 질문 0개·5개 이상·목록 밖 태그·빈 질문·넘겨주지 않은 첨부 출처면 AI_FAILED")
  void validate_invalid() {
    assertAiFailed(new RecordQuestionResult(false, List.of()));
    assertAiFailed(
        new RecordQuestionResult(false, Collections.nCopies(5, q("근거와 숫자", null, null))));
    assertAiFailed(new RecordQuestionResult(false, List.of(q("느낀 점", null, null))));
    assertAiFailed(
        new RecordQuestionResult(false, List.of(new Question("근거와 숫자", null, null, " "))));
    assertAiFailed(new RecordQuestionResult(false, List.of(q("선택의 이유", "시안", 999L))));
  }

  @Test
  @DisplayName("프롬프트: 태그 목록·메모·기록·첨부(번호·종류·이름·본문)가 이름표 구역으로 들어가고, system에 category 기준이 있다")
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
            "[첨부]\n(601) FILE / MEETING_NOTE / 회의록.pdf: 본문");
    assertThat(RecordPrompts.QUESTIONS_SYSTEM)
        .contains("1 ~ 4개", "디자인(DESIGN)·링크(LINK) → 선택의 이유", "기타(OTHER)", "30%");
  }
}
