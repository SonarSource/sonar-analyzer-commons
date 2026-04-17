/*
 * SonarSource Analyzers Regex Parsing Commons
 * Copyright (C) SonarSource Sàrl
 * mailto:info AT sonarsource DOT com
 *
 * You can redistribute and/or modify this program under the terms of
 * the Sonar Source-Available License Version 1, as published by SonarSource Sàrl.
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
