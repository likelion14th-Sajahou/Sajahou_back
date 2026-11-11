package com.sajarok.infra.ai;

public final class EvidenceVerifier {

  private EvidenceVerifier() {}

  public static boolean isQuoted(String original, String quote) {
    String normalizedQuote = normalize(quote);
    return !normalizedQuote.isBlank() && normalize(original).contains(normalizedQuote);
  }

  private static String normalize(String text) {
    return text == null ? "" : text.replaceAll("\\s+", " ").strip();
  }
}
