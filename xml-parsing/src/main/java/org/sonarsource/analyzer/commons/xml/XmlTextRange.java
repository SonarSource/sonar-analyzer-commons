/*
 * SonarSource Analyzers XML Parsing Commons
 * Copyright (C) 2009-2025 SonarSource SA
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
package org.sonarsource.analyzer.commons.xml;

public class XmlTextRange {
  private final int startLine;
  private final int startColumn;
  private final int endLine;
  private final int endColumn;

  public XmlTextRange(int startLine, int startColumn, int endLine, int endColumn) {
    if (startLine > endLine) {
      throw new IllegalArgumentException("Cannot have a start line after end line");
    }
    if (startLine == endLine && startColumn > endColumn) {
      throw new IllegalArgumentException("Cannot have a start column after end column when on same line");
    }
    if (startLine == endLine && startColumn == endColumn) {
      throw new IllegalArgumentException("Cannot have an empty range");
    }
    if (startLine < 1 || endLine < 1) {
      throw new IllegalArgumentException("Cannot have a line less than 1");
    }
    if (startColumn < 0 || endColumn < 0) {
      throw new IllegalArgumentException("Cannot have a line less than 1");
    }
    this.startLine = startLine;
    this.startColumn = startColumn;
    this.endLine = endLine;
    this.endColumn = endColumn;
  }

  XmlTextRange(XmlFilePosition start, XmlFilePosition end, XmlFilePosition xmlStart) {
    this(start.computeSqLine(xmlStart), start.computeSqColumn(xmlStart), end.computeSqLine(xmlStart), end.computeSqColumn(xmlStart));
  }

  XmlTextRange(XmlTextRange start, XmlFilePosition end, XmlFilePosition xmlStart) {
    this(start.startLine, start.startColumn, end.computeSqLine(xmlStart), end.computeSqColumn(xmlStart));
  }

  public XmlTextRange(XmlTextRange start, XmlTextRange end) {
    this(start.startLine, start.startColumn, end.endLine, end.endColumn);
  }

  /**
   * @return 1-based line number of a range start
   */
  public int getStartLine() {
    return startLine;
  }

  /**
   * @return 0-based column number of a range start
   */
  public int getStartColumn() {
    return startColumn;
  }

  /**
   * @return 1-based line number of a range end
   */
  public int getEndLine() {
    return endLine;
  }

  /**
   * @return 0-based column number of a range end
   */
  public int getEndColumn() {
    return endColumn;
  }

  @Override
  public String toString() {
    return "{" + startLine +
      ":" + startColumn +
      " - " + endLine +
      ":" + endColumn +
      '}';
  }
}
