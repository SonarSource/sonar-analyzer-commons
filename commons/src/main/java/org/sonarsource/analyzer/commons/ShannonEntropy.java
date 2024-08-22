package org.sonarsource.analyzer.commons;

import java.util.HashMap;
import javax.annotation.Nullable;

public class ShannonEntropy {
  private static final double LOG_2 = Math.log(2.0d);

  private ShannonEntropy() {
    // utility class
  }

  public static double calculate(@Nullable String str) {
    if (str == null || str.isEmpty()) {
      return 0.0d;
    }
    int length = str.length();
    return str.chars()
      .collect(HashMap<Integer, Integer>::new, (map, ch) -> map.merge(ch, 1, Integer::sum), HashMap::putAll)
      .values().stream()
      .mapToDouble(count -> ((double) count) / length)
      .map(frequency -> -frequency * Math.log(frequency))
      .sum() / LOG_2;
  }
}
