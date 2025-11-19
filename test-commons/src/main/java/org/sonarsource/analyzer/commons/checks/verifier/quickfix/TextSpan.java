/*
 * SonarSource Analyzers Test Commons
 * Copyright (C) 2009-2025 SonarSource Sàrl
 * mailto:info AT sonarsource DOT com
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the Sonar Source-Available License Version 1, as published by SonarSource SA.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the Sonar Source-Available License for more details.
 *
 * You should have received a copy of the Sonar Source-Available License
 * along with this program; if not, see https://sonarsource.com/license/ssal/
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
    int prime = 29;
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
