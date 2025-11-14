/*
 * SonarSource Analyzers Regex Parsing Commons
 * Copyright (C) 2009-2025 SonarSource Sàrl
 * mailto:info AT sonarsource DOT com
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the Sonar Source-Available License Version 1, as published by SonarSource Sàrl.
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

import java.util.Collections;
import java.util.List;
import org.sonarsource.analyzer.commons.regex.ast.RegexSyntaxElement;

public class RegexIssueLocation {

  private final List<RegexSyntaxElement> syntaxElements;
  private final String message;

  public RegexIssueLocation(RegexSyntaxElement syntaxElement, String message) {
    this(Collections.singletonList(syntaxElement), message);
  }

  public RegexIssueLocation(List<RegexSyntaxElement> syntaxElements, String message) {
    this.syntaxElements = syntaxElements;
    this.message = message;
  }

  public List<RegexSyntaxElement> syntaxElements() {
    return syntaxElements;
  }

  public String message() {
    return message;
  }
}
