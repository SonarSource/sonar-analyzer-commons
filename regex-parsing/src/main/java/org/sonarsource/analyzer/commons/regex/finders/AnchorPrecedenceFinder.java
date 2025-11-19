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
package org.sonarsource.analyzer.commons.regex.finders;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import org.sonarsource.analyzer.commons.regex.RegexIssueReporter;
import org.sonarsource.analyzer.commons.regex.ast.BoundaryTree;
import org.sonarsource.analyzer.commons.regex.ast.DisjunctionTree;
import org.sonarsource.analyzer.commons.regex.ast.NonCapturingGroupTree;
import org.sonarsource.analyzer.commons.regex.ast.RegexBaseVisitor;
import org.sonarsource.analyzer.commons.regex.ast.RegexTree;
import org.sonarsource.analyzer.commons.regex.ast.SequenceTree;

public class AnchorPrecedenceFinder extends RegexBaseVisitor {

  public static final String MESSAGE = "Group parts of the regex together to make the intended operator precedence explicit.";

  private final RegexIssueReporter.ElementIssue regexElementIssueReporter;

  private enum Position {
    BEGINNING, END
  }

  public AnchorPrecedenceFinder(RegexIssueReporter.ElementIssue regexElementIssueReporter) {
    this.regexElementIssueReporter = regexElementIssueReporter;
  }

  @Override
  public void visitDisjunction(DisjunctionTree tree) {
    List<RegexTree> alternatives = tree.getAlternatives();
    if ((anchoredAt(alternatives, Position.BEGINNING) || anchoredAt(alternatives, Position.END))
      && notAnchoredElseWhere(alternatives)) {
      regexElementIssueReporter.report(tree, MESSAGE, null, Collections.emptyList());
    }
    super.visitDisjunction(tree);
  }

  private static boolean anchoredAt(List<RegexTree> alternatives, Position position) {
    int itemIndex = position == Position.BEGINNING ? 0 : (alternatives.size() - 1);
    RegexTree firstOrLast = alternatives.get(itemIndex);
    return isAnchored(firstOrLast, position);
  }

  private static boolean notAnchoredElseWhere(List<RegexTree> alternatives) {
    if (isAnchored(alternatives.get(0), Position.END)
      || isAnchored(alternatives.get(alternatives.size() - 1), Position.BEGINNING)) {
      return false;
    }
    for (RegexTree alternative : alternatives.subList(1, alternatives.size() - 1)) {
      if (isAnchored(alternative, Position.BEGINNING) || isAnchored(alternative, Position.END)) {
        return false;
      }
    }
    return true;
  }

  private static boolean isAnchored(RegexTree tree, Position position) {
    if (!tree.is(RegexTree.Kind.SEQUENCE)) {
      return false;
    }
    SequenceTree sequence = (SequenceTree) tree;
    List<RegexTree> items = sequence.getItems().stream()
      .filter(item -> !isFlagSetter(item))
      .collect(Collectors.toList());
    if (items.isEmpty()) {
      return false;
    }
    int index = position == Position.BEGINNING ? 0 : (items.size() - 1);
    RegexTree firstOrLast = items.get(index);
    return firstOrLast.is(RegexTree.Kind.BOUNDARY) && isAnchor((BoundaryTree) firstOrLast);
  }

  private static boolean isAnchor(BoundaryTree tree) {
    switch (tree.type()) {
      case INPUT_START:
      case LINE_START:
      case INPUT_END:
      case INPUT_END_FINAL_TERMINATOR:
      case LINE_END:
        return true;
      default:
        return false;
    }
  }

  /**
   * Return whether the given regex is a non-capturing group without contents, i.e. one that only sets flags for the
   * rest of the expression
   */
  private static boolean isFlagSetter(RegexTree tree) {
    return tree.is(RegexTree.Kind.NON_CAPTURING_GROUP) && ((NonCapturingGroupTree) tree).getElement() == null;
  }
}
