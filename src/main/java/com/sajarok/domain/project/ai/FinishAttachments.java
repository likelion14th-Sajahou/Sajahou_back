package com.sajarok.domain.project.ai;

import com.sajarok.domain.attachment.dto.AttachmentView;
import java.util.List;

/**
 * 32·33번 프롬프트의 [첨부]. 종류와 이름만 넣는다(글자는 넣지 않음).
 *
 * @param artifacts 프로젝트 산출물 첨부 (마치기 '산출물 요청' 답으로 연결된 것)
 * @param recordAttachments 고른 기록의 첨부
 */
public record FinishAttachments(
    List<AttachmentView> artifacts, List<AttachmentView> recordAttachments) {

  public static FinishAttachments none() {
    return new FinishAttachments(List.of(), List.of());
  }
}
