/*
 * SonarSource Analyzers Recognizers
 * Copyright (C) 2009-2021 SonarSource SA
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


public class EndWithDetector extends Detector {

  private final char[] endOfLines;

  public EndWithDetector(double probability, char... endOfLines) {
    super(probability);
    this.endOfLines = endOfLines;
  }

  @Override
  public int scan(String line) {
    for (int index = line.length() - 1; index >= 0; index--) {
      char character = line.charAt(index);
      for (char endOfLine : endOfLines) {
        if (character == endOfLine) {
          return 1;
        }
      }
      if (!Character.isWhitespace(character) && character != '*' && character != '/') {
        return 0;
      }
    }
    return 0;
  }
}
