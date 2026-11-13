package com.sajarok.domain.storycard.service;

import com.sajarok.domain.storycard.ai.ProjectCardTitlePrompts;
import com.sajarok.domain.storycard.ai.ProjectCardTitleResult;
import com.sajarok.global.exception.BusinessException;
import com.sajarok.infra.ai.AiClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 프로젝트 카드 제목 짓기: AI가 카드 전문(기록 전문)을 읽고 제목을 지어 붙인다.
 *
 * <p>기록 저장(27·28번) 트랜잭션이 끝난 뒤, 같은 요청 안에서 부른다. 그래서 저장 응답이 오면 제목도 붙어 있고, AI를 기다리는 동안 DB 연결을 잡지 않는다.
 * AI가 실패하거나 빈 제목을 주면 제목은 빈칸(null)으로 둔다. 기록·카드는 이미 저장됐으므로 여기서 생긴 예외는 밖으로 내보내지 않는다(저장 요청이 실패로 보이면
 * 사용자가 같은 기록을 또 저장할 수 있음).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProjectCardTitler {

  private final StoryCardService storyCardService;
  private final AiClient aiClient;

  public void name(Long cardId) {
    try {
      String recordText = storyCardService.findRecordTextToName(cardId);
      if (recordText == null) {
        return; // 이미 제목이 있거나, 첨부만 있고 쓴 내용이 없음
      }
      String title =
          aiClient
              .call(
                  ProjectCardTitlePrompts.SYSTEM,
                  ProjectCardTitlePrompts.user(recordText),
                  ProjectCardTitleResult.class)
              .clean();
      if (title == null) {
        log.warn("AI가 프로젝트 카드 제목을 비워 보내 빈칸으로 둠 cardId={}", cardId);
        return;
      }
      storyCardService.nameProjectCard(cardId, title);
    } catch (BusinessException e) {
      log.warn("프로젝트 카드 제목을 짓지 못해 빈칸으로 둠 cardId={} code={}", cardId, e.getErrorCode());
    } catch (RuntimeException e) {
      log.warn("프로젝트 카드 제목을 짓지 못해 빈칸으로 둠 cardId={}", cardId, e);
    }
  }
}
