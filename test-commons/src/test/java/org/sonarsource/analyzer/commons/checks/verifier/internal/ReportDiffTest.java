/*
 * SonarSource Analyzers Test Commons
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
package org.sonarsource.analyzer.commons.checks.verifier.internal;

import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ReportDiffTest {

  @Test
  public void same_content() {
    String a = "a\n" +
      "b\n";
    String diff = ReportDiff.diff(a, a);
    assertThat(diff).isEmpty();
  }

  @Test
  public void different_content() {
    String expected = "a\n" +
      "b\n" +
      "E1\n" +
      "c\n" +
      "d\n" +
      "E2\n" +
      "e\n" +
      "f\n";
    String actual = "a\n" +
      "b\n" +
      "U1\n" +
      "c\n" +
      "U2\n" +
      "c\n" +
      "d\n" +
      "e\n" +
      "U3\n" +
      "f\n" +
      "U4\n";
    String diff = ReportDiff.diff(expected, actual);
    assertThat(diff).isEqualTo("  b\n" +
      "- E1\n" +
      "+ U1\n" +
      "+ c\n" +
      "+ U2\n" +
      "  d\n" +
      "- E2\n" +
      "  e\n" +
      "+ U3\n" +
      "  f\n" +
      "+ U4\n");
  }

}
