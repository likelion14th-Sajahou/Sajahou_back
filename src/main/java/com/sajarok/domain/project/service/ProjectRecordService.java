package com.sajarok.domain.project.service;

import com.sajarok.domain.project.repository.ProjectRecordRepository;
import com.sajarok.domain.project.repository.RecordDate;
import com.sajarok.global.exception.BusinessException;
import com.sajarok.global.exception.ErrorCode;
import java.time.LocalDate;
import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProjectRecordService {

  private final ProjectRecordRepository projectRecordRepository;

  /** 기록 주인 회원 ID. 다른 도메인이 소유 확인에 쓴다. 없으면 NOT_FOUND. */
  public Long getOwnerId(Long recordId) {
    return projectRecordRepository
        .findUserIdById(recordId)
        .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
  }

  /**
   * 기록을 지운다. 24번(카드 삭제)에서 카드 담당 컨트롤러가 부르고, 29번(기록 삭제)에서도 쓴다.
   *
   * <p>남의 기록이거나 없으면 NOT_FOUND. 부르는 쪽 트랜잭션에 묶인다(하나라도 실패하면 모두 되돌림).
   *
   * <p>기록을 가리키는 카드·첨부·AI 질문이 남아 있으면 DB가 기록 삭제를 막으므로(FK, CASCADE 없음) 이것들을 먼저 지운다. 카드를 지우면 역량
   * 태그(card_competency)와 자소서 연결(cover_letter_question_card)은 DB CASCADE로 함께 지워진다.
   */
  @Transactional
  public void delete(Long userId, Long recordId) {
    Long ownerId = getOwnerId(recordId);
    if (!ownerId.equals(userId)) {
      throw new BusinessException(ErrorCode.NOT_FOUND);
    }

    // TODO(카드 담당): StoryCardService가 생기면 호출한다.
    // storyCardService.deleteByRecord(recordId);

    // TODO(첨부 담당): AttachmentService가 생기면 호출한다. owner 형태는 아직 정해지지 않았다. S3 파일까지 지운다.
    // attachmentService.deleteAll(owner);

    // TODO(AI 질문 담당): AiQuestionService가 생기면 호출한다. owner 형태는 아직 정해지지 않았다.
    // aiQuestionService.deleteAll(owner);

    projectRecordRepository.deleteById(recordId);
  }

  /** 기록 ID → 기록 날짜(만든 날). 없는 ID는 결과에서 빠진다. */
  public Map<Long, LocalDate> findDates(Collection<Long> recordIds) {
    if (recordIds == null || recordIds.isEmpty()) {
      return Map.of();
    }
    return projectRecordRepository.findDatesByIdIn(recordIds).stream()
        .collect(Collectors.toMap(RecordDate::recordId, r -> r.createdAt().toLocalDate()));
  }
}
