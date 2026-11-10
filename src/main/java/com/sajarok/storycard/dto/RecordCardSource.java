package com.sajarok.storycard.dto;

import java.time.LocalDate;

public record RecordCardSource(
    Long userId,
    Long projectId,
    Long recordId,
    LocalDate date,
    String memo,
    String did,
    String concern,
    String decision,
    String result,
    String nextStep,
    int attachmentCount) {}
