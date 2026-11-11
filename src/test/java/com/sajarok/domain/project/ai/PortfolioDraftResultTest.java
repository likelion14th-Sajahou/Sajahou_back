package com.sajarok.domain.project.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sajarok.domain.project.ai.PortfolioDraftResult.Section;
import com.sajarok.domain.project.entity.Project;
import com.sajarok.domain.project.entity.ProjectRecord;
import com.sajarok.domain.project.entity.ProjectRecordStatus;
import com.sajarok.domain.question.dto.QuestionView;
import com.sajarok.domain.question.entity.QuestionStatus;
import com.sajarok.global.exception.BusinessException;
import com.sajarok.global.exception.ErrorCode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 33번 AI 응답 검증과 프롬프트 조립 테스트. */
class PortfolioDraftResultTest {

  private static List<Section> allSections() {
    return new ArrayList<>(
        List.of(
            new Section("learned", "배운 점"),
            new Section("role", null),
            new Section("contribution", "- 11/03 팀 API 연동을 맡았습니다."),
            new Section("problem", "  "),
            new Section("result", "숫자로 보면: 2주 동안 API 12개"),
            new Section("artifact", null)));
  }

  @Test
  @DisplayName(
      "33번 검증: 6개 key가 다 있으면 통과하고, key 순서(role → … → learned)로 정렬하며 공백뿐인 본문은 빈칸(null)으로 본다")
  void validate_ordersKeysAndTreatsBlankAsNull() {
    Map<String, String> bodies = new PortfolioDraftResult(allSections()).validate();

    assertThat(bodies.keySet())
        .containsExactly("role", "contribution", "problem", "result", "artifact", "learned");
    assertThat(bodies.get("role")).isNull();
    assertThat(bodies.get("problem")).isNull();
    assertThat(bodies.get("contribution")).isEqualTo("- 11/03 팀 API 연동을 맡았습니다.");
  }

  @Test
  @DisplayName("33번 검증: 응답 없음·key 빠짐·key 중복·모르는 key면 AI_FAILED")
  void validate_invalid_throwsAiFailed() {
    List<Section> missing = allSections();
    missing.remove(0);
    List<Section> duplicated = allSections();
    duplicated.set(0, new Section("role", "중복"));
    List<Section> unknown = allSections();
    unknown.set(0, new Section("summary", "모르는 항목"));

    for (List<Section> sections : Arrays.asList(null, missing, duplicated, unknown)) {
      assertThatThrownBy(() -> new PortfolioDraftResult(sections).validate())
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.AI_FAILED);
    }
  }

  @Test
  @DisplayName("프롬프트: 항목 목록·프로젝트·기록·마치기 답(태그·질문·답)이 이름표 구역으로 들어가고, system에 '숫자로 보면'·MM/DD 규칙이 있다")
  void portfolioUser_containsSections() {
    Project project = Project.builder().userId(1L).name("사자록").build();
    ProjectRecord record =
        ProjectRecord.builder()
            .project(project)
            .status(ProjectRecordStatus.SAVED)
            .did("팀 API 연동을 맡았다")
            .build();
    QuestionView answered =
        new QuestionView(
            1L,
            3,
            "근거와 숫자",
            "결과에 숫자가 거의 없어요",
            null,
            "숫자로 보여 줄 수 있는 게 있나요?",
            "2주 동안 API 12개",
            QuestionStatus.ANSWERED);

    String user =
        FinishPrompts.portfolioUser(
            project, List.of(record), List.of(answered), FinishAttachments.none());

    assertThat(user)
        .contains(
            "[항목 목록]",
            "role / contribution / problem / result / artifact / learned",
            "[프로젝트]",
            "[기록]",
            "한 일: 팀 API 연동을 맡았다",
            "[마치기 답]",
            "- 근거와 숫자",
            "답: 2주 동안 API 12개",
            "[첨부]\n없음");
    assertThat(FinishPrompts.PORTFOLIO_DRAFT_SYSTEM)
        .contains("숫자로 보면: …", "- MM/DD 한 일", "\"- 11/03 팀 API 연동을 맡았습니다.\\n- 11/05");
  }
}
