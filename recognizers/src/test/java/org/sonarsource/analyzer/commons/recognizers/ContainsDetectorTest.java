/*
 * SonarSource Analyzers Recognizers
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
package org.sonarsource.analyzer.commons.recognizers;

import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ContainsDetectorTest {

  @Test
  public void scan() {
    ContainsDetector detector = new ContainsDetector(0.3, "++", "for(");
    assertThat(detector.scan("for (int i =0; i++; i<4) {")).isEqualTo(2);
    assertThat(detector.scan("String name;")).isZero();
  }
}
