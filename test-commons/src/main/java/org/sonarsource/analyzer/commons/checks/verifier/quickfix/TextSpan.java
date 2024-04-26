/*
 * SonarSource Analyzers Quickfixes
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
package org.sonarsource.analyzer.commons.checks.verifier.quickfix;

public class TextSpan {

  public final int startLine;
  public final int startCharacter;
  public final int endLine;
  public final int endCharacter;

  public TextSpan(int line) {
    this(line, -1, line, -1);
  }

  public TextSpan(int startLine, int startCharacter, int endLine, int endCharacter) {
    this.startLine = startLine;
    this.startCharacter = startCharacter;
    this.endLine = endLine;
    this.endCharacter = endCharacter;
  }

  @Override
  public String toString() {
    return "(" + startLine + ":" + startCharacter + ")-(" + endLine + ":" + endCharacter + ")";
  }

  public boolean onLine() {
    return startCharacter == -1;
  }

  public boolean isEmpty() {
    return startLine == endLine && startCharacter == endCharacter;
  }

  @Override
  public int hashCode() {
    int prime = 27;
    int result = 1;
    result = prime * result + startLine;
    result = prime * result + startCharacter;
    result = prime * result + endLine;
    result = prime * result + endCharacter;
    return result;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (!(obj instanceof TextSpan)) {
      return false;
    }
    TextSpan other = (TextSpan) obj;
    return startLine == other.startLine
      && startCharacter == other.startCharacter
      && endLine == other.endLine
      && endCharacter == other.endCharacter;
  }

}
