package com.sajarok.domain.project.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.sajarok.domain.project.ai.RecordAttachment;
import com.sajarok.domain.project.entity.Project;
import com.sajarok.domain.project.entity.ProjectRecord;
import com.sajarok.domain.project.entity.ProjectRecordStatus;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 30번 자료 해시 테스트: 무엇이 바뀌면 "자료 바뀜"인지. */
class MaterialHashTest {

  private static ProjectRecord record(String memo, String did) {
    return ProjectRecord.builder()
        .project(Project.builder().userId(1L).name("사자록").build())
        .status(ProjectRecordStatus.DRAFT)
        .memo(memo)
        .did(did)
        .build();
  }

  private static RecordAttachment attachment(long id) {
    return new RecordAttachment(id, "FILE", "MEETING_NOTE", "회의록.pdf", "본문");
  }

  @Test
  @DisplayName("SHA-256 hex 64자이고, 앞뒤 공백만 다르면 같은 해시 (공백 수정은 자료 바뀜이 아님)")
  void sameForWhitespaceOnly() {
    String hash = MaterialHash.of(record("11/3 기획 회의", null), List.of());

    assertThat(hash).hasSize(64).matches("[0-9a-f]{64}");
    assertThat(MaterialHash.of(record("  11/3 기획 회의\n", "  "), List.of())).isEqualTo(hash);
  }

  @Test
  @DisplayName("메모나 5칸 내용이 바뀌면 다른 해시")
  void differsWhenMemoOrFieldChanges() {
    String hash = MaterialHash.of(record("11/3 기획 회의", null), List.of());

    assertThat(MaterialHash.of(record("11/3 기획 회의. 시안 준비", null), List.of())).isNotEqualTo(hash);
    assertThat(MaterialHash.of(record("11/3 기획 회의", "시안 2개 준비"), List.of())).isNotEqualTo(hash);
  }

  @Test
  @DisplayName("첨부는 ID 묶음으로 본다: 순서가 달라도 같고, 첨부가 늘면 다르다")
  void attachmentsByIdSet() {
    ProjectRecord record = record("메모", null);
    String hash = MaterialHash.of(record, List.of(attachment(601), attachment(602)));

    assertThat(MaterialHash.of(record, List.of(attachment(602), attachment(601)))).isEqualTo(hash);
    assertThat(MaterialHash.of(record, List.of(attachment(601)))).isNotEqualTo(hash);
  }

  @Test
  @DisplayName("칸 경계가 섞이지 않는다: 메모 'ab' + 한 일 '' 와 메모 'a' + 한 일 'b'는 다른 해시")
  void fieldBoundariesMatter() {
    assertThat(MaterialHash.of(record("ab", null), List.of()))
        .isNotEqualTo(MaterialHash.of(record("a", "b"), List.of()));
  }
}
