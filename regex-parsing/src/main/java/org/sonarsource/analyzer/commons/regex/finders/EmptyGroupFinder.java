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
package org.sonarsource.analyzer.commons.regex.finders;

import org.sonarsource.analyzer.commons.regex.RegexIssueReporter;
import org.sonarsource.analyzer.commons.regex.ast.GroupTree;
import org.sonarsource.analyzer.commons.regex.ast.RegexBaseVisitor;
import org.sonarsource.analyzer.commons.regex.ast.RegexTree;
import org.sonarsource.analyzer.commons.regex.ast.SequenceTree;

import java.util.Collections;


public class EmptyGroupFinder extends RegexBaseVisitor {
  private static final String MESSAGE = "Remove this empty group.";

  private final RegexIssueReporter.ElementIssue regexElementIssueReporter;

  public EmptyGroupFinder(RegexIssueReporter.ElementIssue regexElementIssueReporter) {
    this.regexElementIssueReporter = regexElementIssueReporter;
  }

  @Override
  public void visitGroup(GroupTree groupTree) {
    RegexTree element = groupTree.getElement();
    if (element != null) {
      if (element instanceof SequenceTree && ((SequenceTree) element).getItems().isEmpty()) {
        regexElementIssueReporter.report(groupTree, MESSAGE, null, Collections.emptyList());
      } else {
        super.visit(element);
      }
    }
  }
}
