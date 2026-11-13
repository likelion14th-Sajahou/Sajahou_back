package com.sajarok.domain.project.service;

import com.sajarok.domain.project.dto.ProjectRecordCreateRequest;
import com.sajarok.domain.project.dto.ProjectRecordResponse;
import com.sajarok.domain.project.dto.ProjectRecordUpdateRequest;
import com.sajarok.domain.storycard.service.ProjectCardTitler;
import com.sajarok.domain.storycard.service.StoryCardService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 27·28번 기록 저장 + 프로젝트 카드 AI 제목. 컨트롤러는 기록 추가·수정을 이 클래스로 부른다.
 *
 * <p>저장은 {@link ProjectRecordService}가 트랜잭션 하나로 끝내고, 그다음 이번 요청에서 새로 생긴 카드에만 AI 제목을 붙인다. AI를 트랜잭션
 * 밖에서 부르려고 둘을 나눴다. 그래서 이 클래스에는 @Transactional을 달지 않는다. 제목을 못 지어도 저장은 성공이고 제목은 빈칸(null)이다.
 */
@Service
@RequiredArgsConstructor
public class ProjectRecordFacade {

  private final ProjectRecordService projectRecordService;
  private final StoryCardService storyCardService;
  private final ProjectCardTitler projectCardTitler;

  /** 27번. '이대로 저장'이면 카드가 이번에 생기므로 제목을 짓는다. 'AI와 정리하기'(DRAFT)는 카드가 없어 건너뛴다. */
  public ProjectRecordResponse create(
      Long userId, Long projectId, ProjectRecordCreateRequest request) {
    ProjectRecordResponse response = projectRecordService.create(userId, projectId, request);
    if (response.cardId() != null) {
      projectCardTitler.name(response.cardId());
    }
    return response;
  }

  /** 28번. DRAFT를 저장(SAVED)해 카드가 이번에 생겼을 때만 제목을 짓는다. 이미 있던 카드의 제목은 기록을 고쳐도 그대로 둔다(H-15). */
  public ProjectRecordResponse update(
      Long userId, Long recordId, ProjectRecordUpdateRequest request) {
    boolean hadCard = !storyCardService.findByRecordIds(List.of(recordId)).isEmpty();
    ProjectRecordResponse response = projectRecordService.update(userId, recordId, request);
    if (!hadCard && response.cardId() != null) {
      projectCardTitler.name(response.cardId());
    }
    return response;
  }
}
