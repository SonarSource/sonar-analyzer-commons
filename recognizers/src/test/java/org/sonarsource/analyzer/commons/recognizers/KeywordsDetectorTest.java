/*
 * SonarSource Analyzers Recognizers
 * Copyright (C) 2009-2022 SonarSource SA
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
