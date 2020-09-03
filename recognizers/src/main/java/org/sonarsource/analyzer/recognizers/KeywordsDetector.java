/*
 * SonarSource Analyzers Recognizers
 * Copyright (C) 2009-2019 SonarSource SA
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
package org.sonarsource.analyzer.recognizers;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.StringTokenizer;

public class KeywordsDetector extends Detector {

  private final List<String> keywords;
  private boolean toUpperCase = false;

  public KeywordsDetector(double probability, String... keywords) {
    super(probability);
    this.keywords = Arrays.asList(keywords);
  }

  public KeywordsDetector(double probability, boolean toUpperCase, String... keywords) {
    this(probability, keywords);
    this.toUpperCase = toUpperCase;
  }

  @Override
  public int scan(String line) {
    int matchers = 0;
    if (toUpperCase) {
      line = line.toUpperCase(Locale.getDefault());
    }
    StringTokenizer tokenizer = new StringTokenizer(line, " \t(),{}");
    while (tokenizer.hasMoreTokens()) {
      String word = tokenizer.nextToken();
      if (keywords.contains(word)) {
        matchers++;
      }
    }
    return matchers;
  }

}
