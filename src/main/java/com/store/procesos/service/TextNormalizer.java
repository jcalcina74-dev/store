package com.store.procesos.service;

import java.text.Normalizer;
import java.util.Locale;

public final class TextNormalizer {
  private TextNormalizer() {}

  public static String display(String text) {
    return Normalizer.normalize(text.strip(), Normalizer.Form.NFKC).replaceAll("\\s+", " ");
  }

  public static String key(String text) {
    return display(text).toLowerCase(Locale.ROOT);
  }
}
