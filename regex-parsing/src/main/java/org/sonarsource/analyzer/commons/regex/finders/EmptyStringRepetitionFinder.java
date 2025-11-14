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
package org.sonarsource.analyzer.commons.regex.finders;

import java.util.Collections;
import org.sonarsource.analyzer.commons.regex.RegexIssueReporter;
import org.sonarsource.analyzer.commons.regex.ast.DisjunctionTree;
import org.sonarsource.analyzer.commons.regex.ast.GroupTree;
import org.sonarsource.analyzer.commons.regex.ast.RegexBaseVisitor;
import org.sonarsource.analyzer.commons.regex.ast.RegexTree;
import org.sonarsource.analyzer.commons.regex.ast.RepetitionTree;
import org.sonarsource.analyzer.commons.regex.ast.SequenceTree;

public class EmptyStringRepetitionFinder extends RegexBaseVisitor {

  private static final String MESSAGE = "Rework this part of the regex to not match the empty string.";

  private final RegexIssueReporter.ElementIssue regexElementIssueReporter;

  public EmptyStringRepetitionFinder(RegexIssueReporter.ElementIssue regexElementIssueReporter) {
    this.regexElementIssueReporter = regexElementIssueReporter;
  }

  @Override
  public void visitRepetition(RepetitionTree tree) {
    RegexTree element = tree.getElement();
    if (matchEmptyString(element)) {
      regexElementIssueReporter.report(element, MESSAGE, null, Collections.emptyList());
    }
  }

  private boolean matchEmptyString(RegexTree element) {
    switch (element.kind()) {
      case SEQUENCE:
        return ((SequenceTree) element).getItems().stream().allMatch(this::matchEmptyString);
      case DISJUNCTION:
        return ((DisjunctionTree) element).getAlternatives().stream().anyMatch(this::matchEmptyString);
      case REPETITION:
        return ((RepetitionTree) element).getQuantifier().getMinimumRepetitions() == 0;
      case LOOK_AROUND:
      case BOUNDARY:
        return true;
      default:
        if (element instanceof GroupTree) {
          RegexTree nestedElement = ((GroupTree) element).getElement();
          return nestedElement == null || matchEmptyString(nestedElement);
        }
        return false;
    }
  }
}
