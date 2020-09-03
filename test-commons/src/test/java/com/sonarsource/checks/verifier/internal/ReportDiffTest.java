/*
 * SonarSource Analyzers Test Commons
 * Copyright (C) 2009-2020 SonarSource SA
 * mailto:info AT sonarsource DOT com
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package com.sonarsource.checks.verifier.internal;

import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ReportDiffTest {

  @Test
  public void same_content() {
    String a = "a\n" +
      "b\n";
    String diff = ReportDiff.diff(a, a);
    assertThat(diff).isEqualTo("");
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
