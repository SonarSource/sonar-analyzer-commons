/*
 * SonarSource Analyzers Commons
 * Copyright (C) 2009-2025 SonarSource SA
 * mailto:info AT sonarsource DOT com
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the Sonar Source-Available License Version 1, as published by SonarSource SA.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the Sonar Source-Available License for more details.
 *
 * You should have received a copy of the Sonar Source-Available License
 * along with this program; if not, see https://sonarsource.com/license/ssal/
 */
package org.sonarsource.analyzer.commons;

import java.util.function.Function;
import java.util.stream.Collectors;
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
      .boxed()
      .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()))
      .values()
      .stream()
      .map(Long::doubleValue)
      .mapToDouble(count -> count / length)
      .map(frequency -> -frequency * Math.log(frequency))
      .sum() / LOG_2;
  }
}
