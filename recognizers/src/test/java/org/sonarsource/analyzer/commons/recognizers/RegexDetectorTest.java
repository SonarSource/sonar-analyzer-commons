/*
 * SonarSource Analyzers Recognizers
 * Copyright (C) 2009-2024 SonarSource SA
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
package org.sonarsource.analyzer.commons.recognizers;

import org.assertj.core.data.Offset;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

public class RegexDetectorTest {

  @Test(expected = IllegalArgumentException.class)
  public void testNegativeProbability() {
    new RegexDetector("toto", -1);
  }

  @Test(expected = IllegalArgumentException.class)
  public void testProbabilityHigherThan1() {
    new RegexDetector("toto", 1.2);
  }

  @Test
  public void testProbability() {
    RegexDetector pattern = new RegexDetector("toto", 0.3);
    Offset<Double> range = within(0.01);
    assertThat(pattern.recognition(" toto ")).isCloseTo(0.3, range);
    assertThat(pattern.recognition("sql")).isCloseTo(0, range);
    assertThat(pattern.recognition(" toto toto toto ")).isCloseTo(1 - Math.pow(0.7, 3), range);
  }

  @Test
  public void testSeveralMatches() {
    RegexDetector pattern = new RegexDetector("(\\S\\.\\S)", 0.3); // \S is non-whitespace character
    Offset<Double> range = within(0.001);
    assertThat(pattern.recognition(" toto ")).isCloseTo(0.0, range);
    assertThat(pattern.recognition("abc.def ghi jkl")).isCloseTo(0.3, range);
    assertThat(pattern.recognition("abc.def.ghi")).isCloseTo(0.51, range);
    assertThat(pattern.recognition("abc.def ghi.jkl")).isCloseTo(0.51, range);
  }
}
