/*
 * SonarSource Analyzers Recognizers
 * Copyright (C) 2009-2025 SonarSource Sàrl
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
package org.sonarsource.analyzer.commons.recognizers;

public class StringUtils {

  private StringUtils() {
    // utility class
  }

  public static int countMatches(String str, String sub) {
    if (str.isEmpty() || sub.isEmpty()) {
      return 0;
    }
    int count = 0;
    for (int idx = 0; (idx = str.indexOf(sub, idx)) != -1; idx += sub.length()) {
      ++count;
    }
    return count;
  }

}
