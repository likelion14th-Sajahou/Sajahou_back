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

  /** AI 제안이 있으면 그 값을 쓰고(이미 쓴 칸도 답으로 보강됨), null이면 사용자 원문을 써서 사용자 글이 사라지지 않게 한다. */
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
      String value = suggested.get(key) != null ? suggested.get(key) : written.get(key);
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
