package com.sajarok.domain.storycard.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sajarok.domain.storycard.dto.RecordCardSource;
import com.sajarok.domain.storycard.dto.RetroCardDraft;
import com.sajarok.domain.storycard.dto.StoryCardDetailResponse;
import com.sajarok.domain.storycard.dto.StoryCardDetailResponse.CompetencyView;
import com.sajarok.domain.storycard.entity.StoryCard;
import com.sajarok.domain.storycard.entity.StoryCardStatus;
import com.sajarok.domain.storycard.entity.StoryCardType;
import com.sajarok.domain.storycard.repository.StoryCardRepository;
import java.time.LocalDate;
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

/** 서비스 테스트: 19번 회고 카드 초안 저장 (없으면 만들고, 있으면 내용만 덮어쓰고 태그·상태는 유지), 프로젝트 카드 AI 제목 붙이기. */
@ExtendWith(MockitoExtension.class)
class StoryCardServiceTest {

  @Mock private StoryCardRepository storyCardRepository;

  @InjectMocks private StoryCardService storyCardService;

  private static final String EVIDENCE =
      "{\"situation\":[{\"attachmentId\":null,\"quote\":\"N+1 문제\"}]}";

  private static RetroCardDraft draft(String title) {
    return new RetroCardDraft(
        1L,
        101L,
        10L,
        StoryCardType.SESSION,
        title,
        "N+1 문제가 생겼다.",
        "fetch join으로 바꿨다.",
        null,
        "쿼리가 21개에서 2개로 줄었다.",
        null,
        "# 세션 회고\n\n## 실습\n...",
        EVIDENCE);
  }

  @Test
  @DisplayName("19번: 이 회고의 카드가 없으면 DRAFT 카드를 새로 만든다 (역량 태그 0개, 빈 칸은 blanks)")
  void saveRetrospectiveDraft_new() {
    when(storyCardRepository.findByRetrospectiveId(101L)).thenReturn(Optional.empty());
    when(storyCardRepository.save(any(StoryCard.class)))
        .thenAnswer(
            invocation -> {
              StoryCard saved = invocation.getArgument(0);
              ReflectionTestUtils.setField(saved, "id", 301L);
              return saved;
            });

    StoryCardDetailResponse response = storyCardService.saveRetrospectiveDraft(draft("N+1 해결"));

    assertThat(response.cardId()).isEqualTo(301L);
    assertThat(response.type()).isEqualTo(StoryCardType.SESSION);
    assertThat(response.status()).isEqualTo(StoryCardStatus.DRAFT);
    assertThat(response.title()).isEqualTo("N+1 해결");
    assertThat(response.blanks()).containsExactly("reason", "learned");
    assertThat(response.competencies()).isEmpty();
    assertThat(response.evidence()).isEqualTo(EVIDENCE);
  }

  @Test
  @DisplayName("19번: 이미 카드가 있으면 새로 만들지 않고 내용만 덮어쓴다. 고른 역량 태그와 공개 상태는 그대로 (R-20)")
  void saveRetrospectiveDraft_overwrite_keepsCompetenciesAndStatus() {
    StoryCard existing = BeanUtils.instantiateClass(StoryCard.class);
    ReflectionTestUtils.setField(existing, "id", 301L);
    ReflectionTestUtils.setField(existing, "type", StoryCardType.SESSION);
    ReflectionTestUtils.setField(existing, "retrospectiveId", 101L);
    ReflectionTestUtils.setField(existing, "status", StoryCardStatus.PUBLISHED);
    ReflectionTestUtils.setField(existing, "title", "예전 제목");
    existing.replaceCompetencies(Set.of(3L, 21L), true);
    when(storyCardRepository.findByRetrospectiveId(101L)).thenReturn(Optional.of(existing));

    StoryCardDetailResponse response =
        storyCardService.saveRetrospectiveDraft(draft("고친 답으로 만든 제목"));

    assertThat(response.cardId()).isEqualTo(301L);
    assertThat(response.title()).isEqualTo("고친 답으로 만든 제목");
    assertThat(response.status()).isEqualTo(StoryCardStatus.PUBLISHED);
    assertThat(response.competencies()).extracting(CompetencyView::id).containsExactly(3L, 21L);
    verify(storyCardRepository, never()).save(any(StoryCard.class));
  }

  private static RecordCardSource record(String did, String memo) {
    return new RecordCardSource(
        1L, 3L, 77L, LocalDate.of(2026, 11, 13), memo, did, null, null, null, null, 0);
  }

  /** 77번 기록으로 막 만든 프로젝트 카드 (제목은 아직 빈칸) */
  private static StoryCard newProjectCard(String did, String memo) {
    StoryCard card = StoryCard.fromRecord(record(did, memo), Set.of(21L));
    ReflectionTestUtils.setField(card, "id", 412L);
    return card;
  }

  @Test
  @DisplayName("27·28번: 기록으로 만든 프로젝트 카드는 제목이 빈칸이고, 전문은 # 제목 없이 날짜부터 쓴다")
  void fromRecord_titleBlankUntilNamed() {
    StoryCard card = newProjectCard("API 명세 확정", null);

    assertThat(card.getTitle()).isNull();
    assertThat(card.getFullMarkdown())
        .startsWith("2026-11-13 기록\n")
        .contains("## 한 일\n\nAPI 명세 확정");
  }

  @Test
  @DisplayName("AI 제목: 제목이 빈 프로젝트 카드면 전문을 주고, 붙인 제목은 전문 맨 위 # 제목으로도 단다")
  void nameProjectCard_setsTitleAndHeading() {
    StoryCard card = newProjectCard("API 명세 확정", null);
    when(storyCardRepository.findById(412L)).thenReturn(Optional.of(card));

    assertThat(storyCardService.findRecordTextToName(412L)).contains("API 명세 확정");
    storyCardService.nameProjectCard(412L, "API 명세 확정 회의");

    assertThat(card.getTitle()).isEqualTo("API 명세 확정 회의");
    assertThat(card.getFullMarkdown()).startsWith("# API 명세 확정 회의\n\n2026-11-13 기록\n");
    assertThat(storyCardService.findRecordTextToName(412L)).isNull();
  }

  @Test
  @DisplayName("AI 제목: 그사이 제목이 생겼으면 덮어쓰지 않는다")
  void nameProjectCard_keepsExistingTitle() {
    StoryCard card = newProjectCard("API 명세 확정", null);
    ReflectionTestUtils.setField(card, "title", "내가 쓴 제목");
    when(storyCardRepository.findById(412L)).thenReturn(Optional.of(card));

    storyCardService.nameProjectCard(412L, "AI 제목");

    assertThat(card.getTitle()).isEqualTo("내가 쓴 제목");
  }

  @Test
  @DisplayName("AI 제목: 쓴 내용 없이 첨부만 있는 기록이거나 회고 카드면 지을 것이 없다(null)")
  void findRecordTextToName_nothingToName() {
    StoryCard retro = BeanUtils.instantiateClass(StoryCard.class);
    ReflectionTestUtils.setField(retro, "type", StoryCardType.SESSION);
    ReflectionTestUtils.setField(retro, "fullMarkdown", "# 세션 회고\n\n## 실습\n...");
    when(storyCardRepository.findById(412L)).thenReturn(Optional.of(newProjectCard(null, null)));
    when(storyCardRepository.findById(301L)).thenReturn(Optional.of(retro));

    assertThat(storyCardService.findRecordTextToName(412L)).isNull();
    assertThat(storyCardService.findRecordTextToName(301L)).isNull();
    assertThat(storyCardService.findRecordTextToName(999L)).isNull();
  }

  @Test
  @DisplayName("28번 기록 수정: AI가 붙인 제목은 그대로 두고 전문만 새 내용으로 바꾼다 (H-15)")
  void syncFromRecord_keepsAiTitle() {
    StoryCard card = newProjectCard("API 명세 확정", null);
    card.nameByAi("API 명세 확정 회의");
    when(storyCardRepository.findByProjectRecordId(77L)).thenReturn(Optional.of(card));

    storyCardService.syncFromRecord(record("API 명세 확정하고 팀에 공유", null), null);

    assertThat(card.getTitle()).isEqualTo("API 명세 확정 회의");
    assertThat(card.getFullMarkdown())
        .startsWith("# API 명세 확정 회의\n\n")
        .contains("API 명세 확정하고 팀에 공유");
  }
}
