package com.sajarok.question.domain;

public record QuestionOwner(Kind kind, Long id) {

  public enum Kind {
    RETROSPECTIVE,
    PROJECT_RECORD,
    PROJECT
  }

  public static QuestionOwner retrospective(Long id) {
    return new QuestionOwner(Kind.RETROSPECTIVE, id);
  }

  public static QuestionOwner projectRecord(Long id) {
    return new QuestionOwner(Kind.PROJECT_RECORD, id);
  }

  public static QuestionOwner project(Long id) {
    return new QuestionOwner(Kind.PROJECT, id);
  }
}
