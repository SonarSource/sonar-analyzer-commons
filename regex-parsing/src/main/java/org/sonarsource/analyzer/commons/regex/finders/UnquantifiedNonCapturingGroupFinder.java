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

import java.util.Collections;
import org.sonarsource.analyzer.commons.regex.RegexIssueReporter;
import org.sonarsource.analyzer.commons.regex.ast.NonCapturingGroupTree;
import org.sonarsource.analyzer.commons.regex.ast.RegexBaseVisitor;
import org.sonarsource.analyzer.commons.regex.ast.RegexTree;
import org.sonarsource.analyzer.commons.regex.ast.RepetitionTree;

public class UnquantifiedNonCapturingGroupFinder extends RegexBaseVisitor {

  private static final String MESSAGE = "Unwrap this unnecessarily grouped subpattern.";

  private final RegexIssueReporter.ElementIssue regexElementIssueReporter;

  private boolean quantifiedNonCapturingGroup = false;

  public UnquantifiedNonCapturingGroupFinder(RegexIssueReporter.ElementIssue regexElementIssueReporter) {
    this.regexElementIssueReporter = regexElementIssueReporter;
  }

  @Override
  public void visitRepetition(RepetitionTree tree) {
    quantifiedNonCapturingGroup = tree.getElement().is(RegexTree.Kind.NON_CAPTURING_GROUP);
    super.visitRepetition(tree);
  }

  @Override
  public void visitNonCapturingGroup(NonCapturingGroupTree tree) {
    RegexTree groupElement = tree.getElement();
    if (!(quantifiedNonCapturingGroup || groupElement == null || groupElement.is(RegexTree.Kind.DISJUNCTION))) {
      regexElementIssueReporter.report(tree, MESSAGE, null, Collections.emptyList());
    }
    quantifiedNonCapturingGroup = false;
    super.visitNonCapturingGroup(tree);
  }
}
