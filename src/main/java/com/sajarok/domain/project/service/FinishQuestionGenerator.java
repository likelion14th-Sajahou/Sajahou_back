package com.sajarok.domain.project.service;

import com.sajarok.domain.project.ai.FinishPrompts;
import com.sajarok.domain.project.ai.FinishQuestionResult;
import com.sajarok.domain.project.ai.FinishQuestionResult.Question;
import com.sajarok.domain.project.entity.Project;
import com.sajarok.domain.project.entity.ProjectRecord;
import com.sajarok.domain.question.dto.NewQuestion;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * 32번 AI 마치기 질문: 프롬프트 조립 → AI 호출 → 결과 검증을 한곳에서 한다. 트랜잭션 안에서 부르지 않는다(최대 50초).
 *
 * <p>AI 응답은 FinishQuestionResult.validate를 통과해야 쓴다. 실패하면 AI_FAILED, 시간 초과는 AiClient가 AI_TIMEOUT으로
 * 던진다.
 */
@Component
public class FinishQuestionGenerator {

  /** AiClient 연결 전에 쓰는 고정 질문 (H-17 프로토타입 문구). */
  static final FinishQuestionResult DEFAULT_QUESTIONS =
      new FinishQuestionResult(
          List.of(
              new Question("내 역할", "기록마다 역할이 흩어져 있어요", "프로젝트 전체에서 내가 맡은 범위를 한 문장으로 말하면요?"),
              new Question(
                  "최종 결과", "프로젝트 끝의 결과가 기록에 없어요", "최종 결과는 어땠어요? 시연, 수상, 사용자 반응처럼 확인할 수 있는 것만요."),
              new Question(
                  "근거와 숫자", "결과에 숫자가 거의 없어요", "숫자로 보여 줄 수 있는 게 있나요? 기간, 인터뷰 인원, 사용자 수처럼요."),
              new Question(
                  "산출물 요청",
                  "최종 산출물이 아직 첨부되지 않았어요",
                  "포트폴리오에 넣을 산출물을 첨부해 주세요. 최종 발표 자료, 시연 링크, 피그마가 있으면 좋아요."),
              new Question("배운 점", "프로젝트 전체를 돌아본 문장이 없어요", "다음 프로젝트에 꼭 가져갈 한 가지는요?")));

  public List<NewQuestion> generate(Project project, List<ProjectRecord> records) {
    String user = FinishPrompts.questionsUser(project, records);

    // TODO(AI 모듈 담당): AiClient가 생기면 아래 고정 질문 대신 호출한다.
    // FinishQuestionResult raw =
    //     aiClient.call(FinishPrompts.QUESTIONS_SYSTEM, user, FinishQuestionResult.class);
    FinishQuestionResult raw = DEFAULT_QUESTIONS;

    return raw.validate();
  }
}
