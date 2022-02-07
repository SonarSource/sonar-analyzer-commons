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


public class CamelCaseDetector extends Detector {

  public CamelCaseDetector(double probability) {
    super(probability);
  }

  @Override
  public int scan(String line) {
    char previousChar = ' ';
    char indexChar;
    for (int i = 0; i < line.length(); i++) {
      indexChar = line.charAt(i);
      if (isLowerCaseThenUpperCase(previousChar, indexChar)) {
        return 1;
      }
      previousChar = indexChar;
    }
    return 0;
  }

  private static boolean isLowerCaseThenUpperCase(char previousChar, char indexChar) {
    return Character.getType(previousChar) == Character.LOWERCASE_LETTER && Character.getType(indexChar) == Character.UPPERCASE_LETTER;
  }
}
