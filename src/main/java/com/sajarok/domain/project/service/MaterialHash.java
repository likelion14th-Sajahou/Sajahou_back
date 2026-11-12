package com.sajarok.domain.project.service;

import com.sajarok.domain.project.ai.RecordAttachment;
import com.sajarok.domain.project.entity.ProjectRecord;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 30번 자료 해시. 대상은 프롬프트에 들어가는 자료(메모·5칸·DONE 첨부 ID)이고, 30번 답은 넣지 않는다.
 *
 * <p>칸마다 앞뒤 공백을 지워서 공백만 바뀐 것은 "자료 바뀜"으로 보지 않는다. SHA-256 hex = 64자(question_input_hash CHAR(64)).
 */
final class MaterialHash {

  private MaterialHash() {}

  static String of(ProjectRecord record, List<RecordAttachment> attachments) {
    String fields =
        Stream.of(
                record.getMemo(),
                record.getDid(),
                record.getConcern(),
                record.getDecision(),
                record.getResult(),
                record.getNextStep())
            .map(v -> v == null ? "" : v.strip())
            .collect(Collectors.joining("\u0000"));
    String attachmentIds =
        attachments.stream()
            .map(RecordAttachment::id)
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
