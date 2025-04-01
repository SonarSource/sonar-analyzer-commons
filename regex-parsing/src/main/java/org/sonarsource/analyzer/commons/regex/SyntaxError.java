/*
 * SonarSource Analyzers Regex Parsing Commons
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
package org.sonarsource.analyzer.commons.regex;

import org.sonarsource.analyzer.commons.regex.ast.IndexRange;
import org.sonarsource.analyzer.commons.regex.ast.RegexSyntaxElement;

public class SyntaxError {

  private final RegexSyntaxElement offendingSyntaxElement;

  private final String message;

  public SyntaxError(RegexSyntaxElement offendingSyntaxElement, String message) {
    this.offendingSyntaxElement = offendingSyntaxElement;
    this.message = message;
  }

  public IndexRange range() {
    return offendingSyntaxElement.getRange();
  }

  public RegexSyntaxElement getOffendingSyntaxElement() {
    return offendingSyntaxElement;
  }

  public String getMessage() {
    return message;
  }

  @Override
  public String toString() {
    return message;
  }
}
