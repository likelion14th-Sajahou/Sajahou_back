package com.sajarok.domain.project.dto;

import com.sajarok.domain.project.ai.RecordOrganizeResult;
import com.sajarok.domain.project.entity.ProjectRecord;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 31번 기록 정리 제안 (저장하지 않음, 저장은 28번). 빈 칸은 null이고 blanks에 key가 들어간다. */
public record RecordOrganizeResponse(
    String did,
    String concern,
    String decision,
    String result,
    String nextStep,
    List<String> blanks) {

  /** 사용자가 이미 쓴 칸은 AI 제안 대신 원문을 그대로 쓴다(사용자 글이 바뀌지 않게). */
  public static RecordOrganizeResponse of(ProjectRecord record, Map<String, String> suggested) {
    Map<String, String> written = new LinkedHashMap<>();
    written.put("did", record.getDid());
    written.put("concern", record.getConcern());
    written.put("decision", record.getDecision());
    written.put("result", record.getResult());
    written.put("nextStep", record.getNextStep());

    Map<String, String> merged = new LinkedHashMap<>();
    List<String> blanks = new ArrayList<>();
    for (String key : RecordOrganizeResult.KEYS) {
      String value = written.get(key) != null ? written.get(key) : suggested.get(key);
      merged.put(key, value);
      if (value == null) {
        blanks.add(key);
      }
    }
    return new RecordOrganizeResponse(
        merged.get("did"),
        merged.get("concern"),
        merged.get("decision"),
        merged.get("result"),
        merged.get("nextStep"),
        blanks);
  }
}
