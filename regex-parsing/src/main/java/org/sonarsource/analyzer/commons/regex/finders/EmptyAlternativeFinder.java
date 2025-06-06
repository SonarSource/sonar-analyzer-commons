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
import java.util.Deque;
import java.util.LinkedList;
import java.util.List;
import org.sonarsource.analyzer.commons.regex.RegexIssueReporter;
import org.sonarsource.analyzer.commons.regex.ast.DisjunctionTree;
import org.sonarsource.analyzer.commons.regex.ast.GroupTree;
import org.sonarsource.analyzer.commons.regex.ast.RegexBaseVisitor;
import org.sonarsource.analyzer.commons.regex.ast.RegexTree;
import org.sonarsource.analyzer.commons.regex.ast.RepetitionTree;
import org.sonarsource.analyzer.commons.regex.ast.SequenceTree;
import org.sonarsource.analyzer.commons.regex.ast.SourceCharacter;


public class EmptyAlternativeFinder extends RegexBaseVisitor {

  private static final String MESSAGE = "Remove this empty alternative.";

  private final RegexIssueReporter.ElementIssue regexElementIssueReporter;

  private final Deque<RegexTree> hierarchyStack = new LinkedList<>();

  public EmptyAlternativeFinder(RegexIssueReporter.ElementIssue regexElementIssueReporter) {
    this.regexElementIssueReporter = regexElementIssueReporter;
  }

  @Override
  public void visitGroup(GroupTree tree) {
    hierarchyStack.addLast(tree);
    super.visitGroup(tree);
    hierarchyStack.removeLast();
  }

  @Override
  public void visitRepetition(RepetitionTree tree) {
    hierarchyStack.addLast(tree);
    super.visitRepetition(tree);
    hierarchyStack.removeLast();
  }

  @Override
  public void visitDisjunction(DisjunctionTree tree) {
    List<RegexTree> alternatives = tree.getAlternatives();
    int nAlternatives = alternatives.size();
    boolean firstIsEmpty = false;
    boolean lastIsEmpty = false;
    for (int i = 0; i < nAlternatives; i++) {
      if (isEmptyAlternative(alternatives.get(i))) {
        firstIsEmpty |= (i == 0);
        lastIsEmpty |= (i == nAlternatives - 1);

        if (!parentIsGroup() || parentIsQuantified() || (0 < i && i < nAlternatives - 1)) {
          SourceCharacter orOperator = tree.getOrOperators().get(i < nAlternatives - 1 ? i : (i - 1));
          regexElementIssueReporter.report(orOperator, MESSAGE, null, Collections.emptyList());
        }
      }
    }

    if (parentIsGroup() && firstIsEmpty && lastIsEmpty) {
      regexElementIssueReporter.report(tree.getOrOperators().get(0), MESSAGE, null, Collections.emptyList());
    }

    super.visitDisjunction(tree);
  }

  private boolean parentIsGroup() {
    return hierarchyStack.peekLast() instanceof GroupTree;
  }

  private boolean parentIsQuantified() {
    RegexTree lastElement = hierarchyStack.pollLast();
    boolean parentIsQuantified = hierarchyStack.peekLast() instanceof RepetitionTree;
    hierarchyStack.addLast(lastElement);
    return parentIsQuantified;
  }

  private static boolean isEmptyAlternative(RegexTree alternative) {
    return alternative.is(RegexTree.Kind.SEQUENCE) && ((SequenceTree) alternative).getItems().isEmpty();
  }
}
