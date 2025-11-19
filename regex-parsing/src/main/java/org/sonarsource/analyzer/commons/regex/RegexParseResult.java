/*
 * SonarSource Analyzers Regex Parsing Commons
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
package org.sonarsource.analyzer.commons.regex;

import java.util.Collections;
import java.util.List;
import org.sonarsource.analyzer.commons.regex.ast.FinalState;
import org.sonarsource.analyzer.commons.regex.ast.FlagSet;
import org.sonarsource.analyzer.commons.regex.ast.OpeningQuote;
import org.sonarsource.analyzer.commons.regex.ast.RegexSyntaxElement;
import org.sonarsource.analyzer.commons.regex.ast.RegexTree;
import org.sonarsource.analyzer.commons.regex.ast.StartState;

public class RegexParseResult {

  private final RegexTree result;

  private final List<SyntaxError> syntaxErrors;

  private final boolean containsComments;

  private final StartState startState;

  private final FinalState finalState;

  public RegexParseResult(RegexTree result, StartState startState, FinalState finalState, List<SyntaxError> syntaxErrors, boolean containsComments) {
    this.result = result;
    this.startState = startState;
    this.finalState = finalState;
    this.syntaxErrors = Collections.unmodifiableList(syntaxErrors);
    this.containsComments = containsComments;
  }

  public RegexTree getResult() {
    return result;
  }

  public FlagSet getInitialFlags() {
    return startState.activeFlags();
  }

  public List<SyntaxError> getSyntaxErrors() {
    return syntaxErrors;
  }

  public boolean hasSyntaxErrors() {
    return !syntaxErrors.isEmpty();
  }

  public boolean containsComments() {
    return containsComments;
  }

  /**
   * Returns a syntax element representing the first opening quote of the string literal(s) making up the regex
   */
  public RegexSyntaxElement openingQuote() {
    return new OpeningQuote(result.getSource());
  }

  public FinalState getFinalState() {
    return finalState;
  }

  public StartState getStartState() {
    return startState;
  }

}
