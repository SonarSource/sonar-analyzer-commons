/*
 * SonarSource Analyzers Recognizers
 * Copyright (C) 2009-2025 SonarSource Sàrl
 * mailto:info AT sonarsource DOT com
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the Sonar Source-Available License Version 1, as published by SonarSource Sàrl.
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

import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class KeywordsDetectorTest {

  @Test
  public void scan() {
    KeywordsDetector detector = new KeywordsDetector(0.3, "public", "static");
    assertThat(detector.scan("public static void main")).isEqualTo(2);
    assertThat(detector.scan("private(static} String name;")).isOne();
    assertThat(detector.scan("publicstatic")).isZero();
    assertThat(detector.scan("i++;")).isZero();
    detector = new KeywordsDetector(0.3, true, "PUBLIC");
    assertThat(detector.scan("Public static pubLIC")).isEqualTo(2);
  }
}
