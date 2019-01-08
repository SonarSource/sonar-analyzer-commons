/*
 * SonarQube Analyzer Commons
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
package org.sonarsource.analyzer.commons;

/**
 * A utility class to compute the offsets which should be provided to SonarQube APIs (highlighting, CPD).
 */
public class TokenLocation {

  private final int startLine;
  private final int startLineOffset;
  private final int endLine;
  private final int endLineOffset;

  /**
   * Constructor which computes end line and column based on the token content.
   * @param line the line at which the token starts (lines start at 1)
   * @param column the column at which the token starts (columns start at 0)
   * @param value the content of the token
   */
  public TokenLocation(int line, int column, String value) {
    this.startLine = line;
    this.startLineOffset = column;
    final String[] lines = value.split("\r\n|\n|\r", -1);
    if (lines.length > 1) {
      this.endLine = line + lines.length - 1;
      this.endLineOffset = lines[lines.length - 1].length();
    } else {
      this.endLine = startLine;
      this.endLineOffset = startLineOffset + value.length();
    }
  }

  public int startLine() {
    return startLine;
  }

  public int startLineOffset() {
    return startLineOffset;
  }

  public int endLine() {
    return endLine;
  }

  public int endLineOffset() {
    return endLineOffset;
  }

}
