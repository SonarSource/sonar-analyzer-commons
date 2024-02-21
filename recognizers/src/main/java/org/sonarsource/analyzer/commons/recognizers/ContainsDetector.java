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

import java.util.Arrays;
import java.util.List;

import static org.sonarsource.analyzer.commons.recognizers.StringUtils.countMatches;

public class ContainsDetector extends Detector {

  private final List<String> strs;

  public ContainsDetector(double probability, String... strs) {
    super(probability);
    this.strs = Arrays.asList(strs);
  }

  @Override
  public int scan(String line) {
    String lineWithoutWhitespaces = line.replaceAll("\\s+", "");
    int matchers = 0;
    for (String str : strs) {
      matchers += countMatches(lineWithoutWhitespaces, str);
    }
    return matchers;
  }
}
