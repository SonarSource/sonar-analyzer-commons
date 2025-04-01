/*
 * SonarSource Analyzers Test Commons
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
package org.sonarsource.analyzer.commons.checks.verifier.quickfix;

public class TextEdit {

  private final TextSpan textSpan;
  private final String replacement;

  private TextEdit(TextSpan textSpan, String replacement) {
    this.textSpan = textSpan;
    this.replacement = replacement;
  }

  @Override
  public String toString() {
    return textSpan.toString() + " -> " + "{{" + replacement + "}}";
  }

  public TextSpan getTextSpan() {
    return textSpan;
  }

  public String getReplacement() {
    return replacement;
  }

  public static TextEdit removeTextSpan(TextSpan textSpan) {
    return new TextEdit(textSpan, "");
  }

  public static TextEdit replaceTextSpan(TextSpan textSpan, String replacement) {
    return new TextEdit(textSpan, replacement);
  }

  public static TextEdit insertAtPosition(int line, int column, String addition) {
    return new TextEdit(position(line, column), addition);
  }

  public static TextSpan position(int line, int column) {
    return textSpan(line, column, line, column);
  }

  public static TextSpan textSpan(int startLine, int startColumn, int endLine, int endColumn) {
    return new TextSpan(startLine, startColumn, endLine, endColumn);
  }

  @Override
  public boolean equals(Object obj) {
    return obj instanceof TextEdit && textSpan.equals(((TextEdit) obj).textSpan) && replacement.equals(((TextEdit) obj).replacement);
  }

  @Override
  public int hashCode() {
    return 17 * textSpan.hashCode() + 31 * replacement.hashCode();
  }
}
