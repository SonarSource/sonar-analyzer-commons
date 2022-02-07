/*
 * SonarSource Analyzers Regex Parsing Commons
 * Copyright (C) 2009-2022 SonarSource SA
 * mailto:info AT sonarsource DOT com
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package org.sonarsource.analyzer.commons.regex.finders;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;
import org.sonarsource.analyzer.commons.regex.RegexIssueReporter;
import org.sonarsource.analyzer.commons.regex.RegexParseResult;
import org.sonarsource.analyzer.commons.regex.ast.AutomatonState;
import org.sonarsource.analyzer.commons.regex.ast.BoundaryTree;
import org.sonarsource.analyzer.commons.regex.ast.DisjunctionTree;
import org.sonarsource.analyzer.commons.regex.ast.LookAroundTree;
import org.sonarsource.analyzer.commons.regex.ast.RegexBaseVisitor;
import org.sonarsource.analyzer.commons.regex.ast.RegexTree;
import org.sonarsource.analyzer.commons.regex.helpers.RegexReachabilityChecker;

public class ImpossibleBoundaryFinder extends RegexBaseVisitor {

  private static final String MESSAGE = "Remove or replace this boundary that will never match because it appears %s mandatory input.";
  private static final String SOFT_MESSAGE =
    "Remove or replace this boundary that can only match if the previous part matched the empty string because it appears %s mandatory input.";
  private final Set<RegexTree> excluded = new HashSet<>();
  private final RegexReachabilityChecker regexReachabilityChecker = new RegexReachabilityChecker(false);

  private final RegexIssueReporter.ElementIssue regexElementIssueReporter;

  private AutomatonState start;
  private AutomatonState end;

  public ImpossibleBoundaryFinder(RegexIssueReporter.ElementIssue regexElementIssueReporter) {
    this.regexElementIssueReporter = regexElementIssueReporter;
  }

  @Override
  public void visit(RegexParseResult regexParseResult) {
    regexReachabilityChecker.clearCache();
    start = regexParseResult.getStartState();
    end = regexParseResult.getFinalState();
    super.visit(regexParseResult);
  }

  @Override
  public void visitLookAround(LookAroundTree tree) {
    // Inside a lookaround we consider the end/start of the lookahead/behind respectively as if it were the end/start
    // of the regex. This avoids false positives for cases like `(?=.*$)foo` or `foo(?<=^...)`.
    if (tree.getDirection() == LookAroundTree.Direction.BEHIND) {
      AutomatonState oldStart = start;
      start = tree.getElement();
      super.visitLookAround(tree);
      start = oldStart;
    } else {
      AutomatonState oldEnd = end;
      // Set end to the lookaround's end-of-lookaround state
      end = tree.getElement().continuation();
      super.visitLookAround(tree);
      end = oldEnd;
    }
  }

  @Override
  public void visitDisjunction(DisjunctionTree tree) {
    BoundaryInDisjunctionFinder boundaryInDisjunctionFinder = new BoundaryInDisjunctionFinder();
    boundaryInDisjunctionFinder.visit(tree);
    excluded.addAll(boundaryInDisjunctionFinder.foundBoundaries());
    super.visitDisjunction(tree);
  }

  @Override
  public void visitBoundary(BoundaryTree boundaryTree) {
    switch (boundaryTree.type()) {
      case LINE_START:
        if (!boundaryTree.activeFlags().contains(Pattern.MULTILINE)) {
          checkStartBoundary(boundaryTree);
        }
        break;
      case INPUT_START:
        checkStartBoundary(boundaryTree);
        break;
      case LINE_END:
        if (!boundaryTree.activeFlags().contains(Pattern.MULTILINE)) {
          checkEndBoundary(boundaryTree);
        }
        break;
      case INPUT_END:
      case INPUT_END_FINAL_TERMINATOR:
        checkEndBoundary(boundaryTree);
        break;
      default:
        // Do nothing
    }
  }

  private void checkStartBoundary(BoundaryTree boundaryTree) {
    if (!RegexReachabilityChecker.canReachWithoutConsumingInput(start, boundaryTree)) {
      regexElementIssueReporter.report(boundaryTree, String.format(MESSAGE, "after"), null, Collections.emptyList());
    } else if (!excluded.contains(boundaryTree) && probablyShouldConsumeInput(start, boundaryTree)) {
      regexElementIssueReporter.report(boundaryTree, String.format(SOFT_MESSAGE, "after"), null, Collections.emptyList());
    }
  }

  private void checkEndBoundary(BoundaryTree boundaryTree) {
    if (!RegexReachabilityChecker.canReachWithoutConsumingInput(boundaryTree, end)) {
      regexElementIssueReporter.report(boundaryTree, String.format(MESSAGE, "before"), null, Collections.emptyList());
    } else if (!excluded.contains(boundaryTree) && probablyShouldConsumeInput(boundaryTree, end)) {
      regexElementIssueReporter.report(boundaryTree, String.format(SOFT_MESSAGE, "before"), null, Collections.emptyList());
    }
  }

  private boolean probablyShouldConsumeInput(AutomatonState start, AutomatonState goal) {
    return regexReachabilityChecker.canReachWithConsumingInput(start, goal, new HashSet<>());
  }

  private static class BoundaryInDisjunctionFinder extends RegexBaseVisitor {
    private final Set<BoundaryTree> foundBoundaries = new HashSet<>();

    @Override
    public void visitBoundary(BoundaryTree boundaryTree) {
      foundBoundaries.add(boundaryTree);
    }

    public Set<BoundaryTree> foundBoundaries() {
      return new HashSet<>(foundBoundaries);
    }
  }
}
