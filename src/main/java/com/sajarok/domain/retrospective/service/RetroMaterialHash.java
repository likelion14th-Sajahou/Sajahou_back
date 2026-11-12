package com.sajarok.domain.retrospective.service;

import com.sajarok.domain.retrospective.ai.RetroAttachment;
import com.sajarok.domain.retrospective.entity.Retrospective;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 17번 자료 해시(R-14). 대상은 프롬프트에 들어가는 자료(실습 여부·실습·과제 방식·과제·코드 리뷰·메모·DONE 첨부 ID)이고, 질문의 답은 넣지 않는다.
 *
 * <p>칸마다 앞뒤 공백을 지워서 공백만 바뀐 것은 "자료 바뀜"으로 보지 않는다. SHA-256 hex = 64자(question_input_hash CHAR(64)).
 * 프로젝트 기록 30번 MaterialHash와 같은 방식.
 */
final class RetroMaterialHash {

  private RetroMaterialHash() {}

  static String of(Retrospective r, List<RetroAttachment> attachments) {
    String fields =
        Stream.of(
                r.getHasPractice() == null ? null : r.getHasPractice().toString(),
                r.getPracticeContent(),
                r.getAssignmentMode() == null ? null : r.getAssignmentMode().name(),
                r.getAssignmentContent(),
                r.getCodeReview(),
                r.getMemo())
            .map(v -> v == null ? "" : v.strip())
            .collect(Collectors.joining("\u0000"));
    String attachmentIds =
        attachments.stream()
            .map(RetroAttachment::id)
            .sorted()
            .map(String::valueOf)
            .collect(Collectors.joining(","));
    return sha256(fields + "\u0000" + attachmentIds);
  }

  private static String sha256(String input) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      return HexFormat.of().formatHex(digest.digest(input.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256을 쓸 수 없음", e);
    }
  }
}
