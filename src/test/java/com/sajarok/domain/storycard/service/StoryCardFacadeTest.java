package com.sajarok.domain.storycard.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.sajarok.domain.competency.service.CompetencyService;
import com.sajarok.domain.project.service.ProjectRecordService;
import com.sajarok.domain.project.service.ProjectService;
import com.sajarok.domain.storycard.dto.RecordCardSource;
import com.sajarok.domain.storycard.dto.StoryCardDetailResponse;
import com.sajarok.domain.storycard.dto.StoryCardDetailResponse.CompetencyView;
import com.sajarok.domain.storycard.dto.StoryCardDetailResponse.SourceRecord;
import com.sajarok.domain.storycard.dto.StoryCardListResponse;
import com.sajarok.domain.storycard.dto.StoryCardListResponse.CardItem;
import com.sajarok.domain.storycard.dto.StoryCardUpdateRequest;
import com.sajarok.domain.storycard.entity.StoryCard;
import com.sajarok.domain.storycard.entity.StoryCardStatus;
import com.sajarok.domain.storycard.entity.StoryCardType;
import com.sajarok.domain.storycard.repository.StoryCardRepository;
import com.sajarok.domain.week.service.WeekService;
import com.sajarok.global.exception.BusinessException;
import com.sajarok.global.exception.ErrorCode;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.BeanUtils;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 서비스 테스트: 카드 목록(21번), 상세(22번), 수정(23번), 삭제(24번)가 카드 종류에 따라 맞게 동작하는지 확인한다.
 *
 * <p>DB 없이 레포지토리와 희선님 프로젝트·기록 서비스, 지윤님 역량 서비스를 가짜(@Mock)로 바꾼다.
 */
@ExtendWith(MockitoExtension.class)
class StoryCardFacadeTest {

  private static final Long ME = 1L;

  @Mock private StoryCardRepository storyCardRepository;
  @Mock private ProjectRecordService projectRecordService;
  @Mock private ProjectService projectService;
  @Mock private CompetencyService competencyService;
  @Mock private WeekService weekService;

  @InjectMocks private StoryCardFacade storyCardFacade;

  /** 77번 기록에서 만든 프로젝트 카드 */
  private static StoryCard projectCard() {
    RecordCardSource src =
        new RecordCardSource(
            ME,
            3L,
            77L,
            LocalDate.of(2026, 11, 3),
            "회의에서 API 명세 확정",
            "API 명세 확정",
            null,
            null,
            null,
            null,
            0);
    StoryCard card = StoryCard.fromRecord(src, Set.of(21L));
    ReflectionTestUtils.setField(card, "id", 412L);
    return card;
  }

  /**
   * 10주차 세션 회고 카드. 회고 카드를 만드는 메서드는 19번에서 생기니, 지금은 빈 카드를 만들어 칸만 채운다.
   *
   * <p>가짜(mock) 카드는 Long 칸이 null이 아니라 0을 돌려줘서 '프로젝트 카드인지'를 잘못 판단하므로 진짜 객체를 쓴다.
   */
  private static StoryCard retrospectiveCard() {
    StoryCard card = BeanUtils.instantiateClass(StoryCard.class); // protected 기본 생성자로 만들기
    ReflectionTestUtils.setField(card, "id", 301L);
    ReflectionTestUtils.setField(card, "userId", ME);
    ReflectionTestUtils.setField(card, "type", StoryCardType.SESSION);
    ReflectionTestUtils.setField(card, "retrospectiveId", 101L);
    ReflectionTestUtils.setField(card, "weekId", 10L);
    ReflectionTestUtils.setField(card, "status", StoryCardStatus.PUBLISHED);
    return card;
  }

  @Test
  @DisplayName("회고 카드는 카드만 지우고, 기록 서비스는 부르지 않는다")
  void retrospectiveCard_deletesCardOnly() {
    StoryCard card = retrospectiveCard();
    when(storyCardRepository.findByIdAndUserId(301L, ME)).thenReturn(Optional.of(card));

    storyCardFacade.delete(ME, 301L);

    verify(storyCardRepository).delete(card);
    verifyNoInteractions(projectRecordService);
  }

  @Test
  @DisplayName("프로젝트 카드는 연결된 기록째 지운다 (카드는 기록 삭제 안에서 지워진다)")
  void projectCard_deletesWithRecord() {
    when(storyCardRepository.findByIdAndUserId(412L, ME)).thenReturn(Optional.of(projectCard()));

    storyCardFacade.delete(ME, 412L);

    verify(projectRecordService).delete(ME, 77L);
    verify(storyCardRepository, never()).delete(any(StoryCard.class));
  }

  @Test
  @DisplayName("없거나 남의 카드면 NOT_FOUND이고, 아무것도 지우지 않는다")
  void notMyCard_notFound() {
    when(storyCardRepository.findByIdAndUserId(999L, ME)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> storyCardFacade.delete(ME, 999L))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.NOT_FOUND);
    verify(storyCardRepository, never()).delete(any(StoryCard.class));
    verifyNoInteractions(projectRecordService);
  }

  @Test
  @DisplayName("22번: 프로젝트 카드 상세에는 프로젝트 이름, 출처 기록, 근거 요약, 빈칸이 들어간다")
  void projectCard_detail() {
    when(storyCardRepository.findByIdAndUserId(412L, ME)).thenReturn(Optional.of(projectCard()));
    when(projectService.names(List.of(3L))).thenReturn(Map.of(3L, "사자록"));
    when(projectRecordService.findDates(List.of(77L)))
        .thenReturn(Map.of(77L, LocalDate.of(2026, 11, 3)));

    StoryCardDetailResponse response = storyCardFacade.getDetail(ME, 412L);

    assertThat(response.cardId()).isEqualTo(412L);
    assertThat(response.type()).isEqualTo(StoryCardType.PROJECT);
    assertThat(response.projectName()).isEqualTo("사자록");
    assertThat(response.action()).isEqualTo("API 명세 확정");
    assertThat(response.blanks()).containsExactly("situation", "reason", "result", "learned");
    assertThat(response.competencies()).containsExactly(new CompetencyView(21L, null));
    assertThat(response.sourceRecord()).isEqualTo(new SourceRecord(77L, LocalDate.of(2026, 11, 3)));
    assertThat(response.evidenceSummary()).containsExactly("2026-11-03 기록");
  }

  @Test
  @DisplayName("22번: 카드 상세의 역량에 이름을 붙인다 (역량 서비스 names)")
  void detail_fillsCompetencyNames() {
    when(storyCardRepository.findByIdAndUserId(412L, ME)).thenReturn(Optional.of(projectCard()));
    when(competencyService.names(Set.of(21L))).thenReturn(Map.of(21L, "협업"));

    StoryCardDetailResponse response = storyCardFacade.getDetail(ME, 412L);

    assertThat(response.competencies()).containsExactly(new CompetencyView(21L, "협업"));
  }

  @Test
  @DisplayName("22번: 회고 카드 상세는 프로젝트·기록을 묻지 않고, 출처 기록 없이 빈 근거 요약을 준다")
  void retrospectiveCard_detail() {
    StoryCard card = retrospectiveCard();
    when(storyCardRepository.findByIdAndUserId(301L, ME)).thenReturn(Optional.of(card));

    StoryCardDetailResponse response = storyCardFacade.getDetail(ME, 301L);

    assertThat(response.type()).isEqualTo(StoryCardType.SESSION);
    assertThat(response.projectName()).isNull();
    assertThat(response.sourceRecord()).isNull();
    assertThat(response.evidenceSummary()).isEmpty();
    verifyNoInteractions(projectService, projectRecordService);
  }

  @Test
  @DisplayName("22번: 없거나 남의 카드면 NOT_FOUND")
  void detail_notMyCard_notFound() {
    when(storyCardRepository.findByIdAndUserId(999L, ME)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> storyCardFacade.getDetail(ME, 999L))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.NOT_FOUND);
  }

  /** 23번 요청. 결과·배운 점·전문은 안 보낸 것으로 둔다 */
  private static StoryCardUpdateRequest request(
      String title, String situation, String action, String reason, Set<Long> competencyIds) {
    return new StoryCardUpdateRequest(
        title, situation, action, reason, null, null, null, competencyIds);
  }

  @Test
  @DisplayName("23번: 보낸 칸만 바뀌고, 빈 문자열은 칸을 비우고, 안 보낸 칸과 태그는 그대로다 (save 없이)")
  void update_changesOnlySentFields() {
    when(storyCardRepository.findByIdAndUserId(412L, ME)).thenReturn(Optional.of(projectCard()));
    when(projectService.names(List.of(3L))).thenReturn(Map.of(3L, "사자록"));
    when(projectRecordService.findDates(List.of(77L)))
        .thenReturn(Map.of(77L, LocalDate.of(2026, 11, 3)));

    StoryCardDetailResponse response =
        storyCardFacade.update(ME, 412L, request("새 제목", null, "", "속도가 중요해서", null));

    assertThat(response.title()).isEqualTo("새 제목");
    assertThat(response.action()).isNull();
    assertThat(response.reason()).isEqualTo("속도가 중요해서");
    assertThat(response.blanks()).containsExactly("situation", "action", "result", "learned");
    assertThat(response.competencies()).containsExactly(new CompetencyView(21L, null));
    verify(storyCardRepository, never()).save(any(StoryCard.class));
  }

  @Test
  @DisplayName("23번: 태그 검사는 역량 서비스에 맡긴다. 공개 카드면 required=true로 부르고, 검사를 통과한 태그로 바꾼다")
  void update_validatesCompetenciesWithCompetencyService() {
    when(storyCardRepository.findByIdAndUserId(412L, ME)).thenReturn(Optional.of(projectCard()));
    when(competencyService.validate(ME, Set.of(3L, 21L), true)).thenReturn(Set.of(3L, 21L));
    when(competencyService.names(Set.of(3L, 21L))).thenReturn(Map.of(3L, "문제 해결", 21L, "협업"));

    StoryCardDetailResponse response =
        storyCardFacade.update(ME, 412L, request(null, null, null, null, Set.of(3L, 21L)));

    assertThat(response.competencies())
        .containsExactly(new CompetencyView(3L, "문제 해결"), new CompetencyView(21L, "협업"));
  }

  @Test
  @DisplayName("23번: 개수가 틀리면(COMPETENCY_LIMIT) 그대로 나가고, 태그 검사를 먼저 해서 같이 보낸 제목도 바뀌지 않는다")
  void update_competencyLimit_changesNothing() {
    StoryCard card = projectCard();
    when(storyCardRepository.findByIdAndUserId(412L, ME)).thenReturn(Optional.of(card));
    when(competencyService.validate(ME, Set.of(), true))
        .thenThrow(new BusinessException(ErrorCode.COMPETENCY_LIMIT));

    assertThatThrownBy(
            () -> storyCardFacade.update(ME, 412L, request("새 제목", null, null, null, Set.of())))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.COMPETENCY_LIMIT);
    assertThat(card.getTitle()).isEqualTo("API 명세 확정");
    assertThat(card.getCompetencyIds()).containsExactly(21L);
  }

  @Test
  @DisplayName("23번: 내 파트 밖의 역량이면 역량 서비스의 400 VALIDATION_ERROR가 그대로 나간다")
  void update_competencyOutsideMyPart_validationError() {
    when(storyCardRepository.findByIdAndUserId(412L, ME)).thenReturn(Optional.of(projectCard()));
    when(competencyService.validate(ME, Set.of(99L), true))
        .thenThrow(new BusinessException(ErrorCode.VALIDATION_ERROR, "선택할 수 없는 역량 태그가 있어요."));

    assertThatThrownBy(
            () -> storyCardFacade.update(ME, 412L, request(null, null, null, null, Set.of(99L))))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.VALIDATION_ERROR);
  }

  @Test
  @DisplayName("23번: 초안 카드는 태그를 0개로 비울 수 있고, 회고 카드라 프로젝트·기록은 묻지 않는다")
  void draftCard_canClearCompetencies() {
    StoryCard card = retrospectiveCard();
    ReflectionTestUtils.setField(card, "status", StoryCardStatus.DRAFT);
    card.changeCompetencies(Set.of(5L));
    when(storyCardRepository.findByIdAndUserId(301L, ME)).thenReturn(Optional.of(card));
    when(competencyService.validate(ME, Set.of(), false))
        .thenReturn(Set.of()); // 초안은 required=false

    StoryCardDetailResponse response =
        storyCardFacade.update(ME, 301L, request(null, null, null, null, Set.of()));

    assertThat(response.status()).isEqualTo(StoryCardStatus.DRAFT);
    assertThat(response.competencies()).isEmpty();
    verifyNoInteractions(projectService, projectRecordService);
  }

  @Test
  @DisplayName("23번: 없거나 남의 카드면 NOT_FOUND")
  void update_notMyCard_notFound() {
    when(storyCardRepository.findByIdAndUserId(999L, ME)).thenReturn(Optional.empty());

    assertThatThrownBy(
            () -> storyCardFacade.update(ME, 999L, request("새 제목", null, null, null, null)))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.NOT_FOUND);
  }

  /** 21번 준비: 레포지토리가 최신순으로 [프로젝트 카드(태그 21), 회고 카드(태그 5)]를 돌려준다 */
  private void twoPublishedCards() {
    StoryCard retro = retrospectiveCard();
    retro.changeCompetencies(Set.of(5L));
    when(storyCardRepository.findAllByUserIdAndStatusOrderByCreatedAtDescIdDesc(
            ME, StoryCardStatus.PUBLISHED))
        .thenReturn(List.of(projectCard(), retro));
  }

  private void projectLookups() {
    when(projectService.names(List.of(3L))).thenReturn(Map.of(3L, "사자록"));
    when(projectRecordService.findDates(List.of(77L)))
        .thenReturn(Map.of(77L, LocalDate.of(2026, 11, 3)));
  }

  @Test
  @DisplayName("21번: 조건이 없으면 공개 카드 전부를 레포지토리 순서(최신순) 그대로 주고, 프로젝트 카드에만 이름·근거 요약을 붙인다")
  void list_noFilter() {
    twoPublishedCards();
    projectLookups();

    StoryCardListResponse response = storyCardFacade.getList(ME, null, null, null, null);

    assertThat(response.totalCount()).isEqualTo(2);
    assertThat(response.cards()).extracting(CardItem::cardId).containsExactly(412L, 301L);
    CardItem project = response.cards().get(0);
    assertThat(project.projectName()).isEqualTo("사자록");
    assertThat(project.evidenceSummary()).containsExactly("2026-11-03 기록");
    CardItem retro = response.cards().get(1);
    assertThat(retro.projectName()).isNull();
    assertThat(retro.evidenceSummary()).isEmpty();
    assertThat(retro.competencies()).containsExactly(new CompetencyView(5L, null));
  }

  @Test
  @DisplayName("21번: type으로 거른다")
  void list_filterByType() {
    twoPublishedCards();
    projectLookups();

    StoryCardListResponse response =
        storyCardFacade.getList(ME, StoryCardType.PROJECT, null, null, null);

    assertThat(response.cards()).extracting(CardItem::cardId).containsExactly(412L);
  }

  @Test
  @DisplayName("21번: competencyId로 거르고, 회고 카드만 남으면 Map.of()에서 null을 꺼내다 터지지 않는다")
  void list_filterByCompetency_onlyRetrospective() {
    twoPublishedCards();
    when(projectService.names(List.of())).thenReturn(Map.of());
    when(projectRecordService.findDates(List.of())).thenReturn(Map.of());

    StoryCardListResponse response = storyCardFacade.getList(ME, null, 5L, null, null);

    assertThat(response.cards()).extracting(CardItem::cardId).containsExactly(301L);
    assertThat(response.cards().get(0).projectName()).isNull();
  }

  @Test
  @DisplayName("21번: q는 제목에서 찾고, 제목이 없는 카드에서도 터지지 않는다. 공백만 보내면 거르지 않는다")
  void list_searchByTitle() {
    twoPublishedCards();
    projectLookups();

    StoryCardListResponse found = storyCardFacade.getList(ME, null, null, null, "API");
    StoryCardListResponse blank = storyCardFacade.getList(ME, null, null, null, "  ");

    assertThat(found.cards()).extracting(CardItem::cardId).containsExactly(412L);
    assertThat(blank.totalCount()).isEqualTo(2);
  }

  @Test
  @DisplayName("21번: 역량 이름은 카드마다 묻지 않고, 목록 전체 태그를 모아 한 번에 가져온다 (N+1 방지)")
  void list_fillsCompetencyNamesAtOnce() {
    twoPublishedCards();
    projectLookups();
    when(competencyService.names(List.of(21L, 5L))).thenReturn(Map.of(21L, "협업", 5L, "문제 해결"));

    StoryCardListResponse response = storyCardFacade.getList(ME, null, null, null, null);

    assertThat(response.cards().get(0).competencies())
        .containsExactly(new CompetencyView(21L, "협업"));
    assertThat(response.cards().get(1).competencies())
        .containsExactly(new CompetencyView(5L, "문제 해결"));
    verify(competencyService).names(List.of(21L, 5L));
  }

  @Test
  @DisplayName("22번: 회고 카드 상세에 주차 번호를 넣는다")
  void detail_weekNumber() {
    when(storyCardRepository.findByIdAndUserId(301L, ME))
        .thenReturn(Optional.of(retrospectiveCard()));
    when(weekService.findWeekNumbers(List.of(10L))).thenReturn(Map.of(10L, 10));

    StoryCardDetailResponse response = storyCardFacade.getDetail(ME, 301L);

    assertThat(response.weekNumber()).isEqualTo(10);
  }

  @Test
  @DisplayName("21번: weekNumber로 거르고(프로젝트 카드는 주차가 없어 빠진다), 목록 카드에 주차 번호를 넣는다")
  void list_filterByWeekNumber() {
    twoPublishedCards();
    when(weekService.findWeekNumbers(List.of(10L))).thenReturn(Map.of(10L, 10));

    StoryCardListResponse week10 = storyCardFacade.getList(ME, null, null, 10, null);
    StoryCardListResponse week9 = storyCardFacade.getList(ME, null, null, 9, null);

    assertThat(week10.cards()).extracting(CardItem::cardId).containsExactly(301L);
    assertThat(week10.cards().get(0).weekNumber()).isEqualTo(10);
    assertThat(week9.totalCount()).isZero();
  }
}
