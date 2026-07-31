/*
 * SonarSource Analyzers Test Commons
 * Copyright (C) SonarSource Sàrl
 * mailto:info AT sonarsource DOT com
 *
 * You can redistribute and/or modify this program under the terms of
 * the Sonar Source-Available License Version 1, as published by SonarSource Sàrl.
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
    String a = """
      a
      b
      """;
    String diff = ReportDiff.diff(a, a);
    assertThat(diff).isEmpty();
  }

  @Test
  public void different_content() {
    String expected = """
      a
      b
      E1
      c
      d
      E2
      e
      f
      """;
    String actual = """
      a
      b
      U1
      c
      U2
      c
      d
      e
      U3
      f
      U4
      """;
    String diff = ReportDiff.diff(expected, actual);
    assertThat(diff).isEqualTo("""
        b
      - E1
      + U1
      + c
      + U2
        d
      - E2
        e
      + U3
        f
      + U4
      """);
  }

}
