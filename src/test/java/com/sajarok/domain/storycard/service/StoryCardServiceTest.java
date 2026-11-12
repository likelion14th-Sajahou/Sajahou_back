package com.sajarok.domain.storycard.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sajarok.domain.storycard.dto.RetroCardDraft;
import com.sajarok.domain.storycard.dto.StoryCardDetailResponse;
import com.sajarok.domain.storycard.dto.StoryCardDetailResponse.CompetencyView;
import com.sajarok.domain.storycard.entity.StoryCard;
import com.sajarok.domain.storycard.entity.StoryCardStatus;
import com.sajarok.domain.storycard.entity.StoryCardType;
import com.sajarok.domain.storycard.repository.StoryCardRepository;
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

/** 서비스 테스트: 19번 회고 카드 초안 저장 (없으면 만들고, 있으면 내용만 덮어쓰고 태그·상태는 유지). */
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
}
