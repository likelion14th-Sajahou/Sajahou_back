package com.sajarok.domain.attachment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sajarok.domain.attachment.dto.AttachmentOwner;
import com.sajarok.domain.attachment.dto.AttachmentView;
import com.sajarok.domain.attachment.entity.AttachmentCategory;
import com.sajarok.global.exception.BusinessException;
import com.sajarok.global.exception.ErrorCode;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class AttachmentConnectTest {

  @Autowired private AttachmentService attachmentService;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private EntityManager entityManager;

  private Long userId;
  private AttachmentOwner recordA;
  private AttachmentOwner recordB;

  @BeforeEach
  void setUp() {
    userId = insertUser();

    jdbc.update(
        "INSERT INTO project (user_id, name, roles) VALUES (?, ?, JSON_ARRAY('DESIGN'))",
        userId,
        "첨부 테스트 프로젝트");
    Long projectId = lastInsertId();

    recordA = insertRecord(projectId);
    recordB = insertRecord(projectId);
  }

  @Test
  void connect_returnsAttachmentOnlyForTheLinkedRecord() {
    Long attachmentId = registerLink(userId);

    attachmentService.connect(userId, List.of(attachmentId), recordA);
    flushAndClear();

    var byRecord = attachmentService.findAll(recordA.kind(), List.of(recordA.id(), recordB.id()));

    assertThat(byRecord).containsOnlyKeys(recordA.id());
    assertThat(byRecord.get(recordA.id()))
        .extracting(AttachmentView::attachmentId)
        .containsExactly(attachmentId);
    assertThat(attachmentService.findAll(recordB)).isEmpty();
  }

  @Test
  void connect_allowsSameRecordAgainButRejectsMovingToAnotherRecord() {
    Long attachmentId = registerLink(userId);

    attachmentService.connect(userId, List.of(attachmentId), recordA);
    flushAndClear();

    attachmentService.connect(userId, List.of(attachmentId), recordA);
    flushAndClear();

    assertThatThrownBy(() -> attachmentService.connect(userId, List.of(attachmentId), recordB))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.INVALID_STATE);

    assertThat(attachmentService.findAll(recordA))
        .extracting(AttachmentView::attachmentId)
        .containsExactly(attachmentId);
    assertThat(attachmentService.findAll(recordB)).isEmpty();
  }

  @Test
  void connect_rejectsEntireRequestWhenAnAttachmentBelongsToAnotherUser() {
    Long myAttachmentId = registerLink(userId);
    Long otherAttachmentId = registerLink(insertUser());
    flushAndClear();

    assertThatThrownBy(
            () ->
                attachmentService.connect(
                    userId, List.of(myAttachmentId, otherAttachmentId), recordA))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.NOT_FOUND);

    flushAndClear();
    assertThat(attachmentService.findAll(recordA)).isEmpty();
  }

  private Long insertUser() {
    String providerId = "attachment-test-" + UUID.randomUUID();
    jdbc.update(
        """
                INSERT INTO users
                  (email, provider_id, name, student_no, department, cohort, part, privacy_agreed_at)
                VALUES (?, ?, '테스트 회원', '00000000', '테스트 학과', 14, 'PLAN_DESIGN', NOW(6))
                """,
        providerId + "@example.invalid",
        providerId);
    return lastInsertId();
  }

  private AttachmentOwner insertRecord(Long projectId) {
    jdbc.update("INSERT INTO project_record (project_id) VALUES (?)", projectId);
    return AttachmentOwner.projectRecord(lastInsertId());
  }

  private Long registerLink(Long ownerUserId) {
    return attachmentService
        .registerLink(ownerUserId, "https://www.figma.com/design/test", AttachmentCategory.DESIGN)
        .getId();
  }

  private Long lastInsertId() {
    return jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
  }

  private void flushAndClear() {
    entityManager.flush();
    entityManager.clear();
  }
}
