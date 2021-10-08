/*
 * SonarSource Analyzers Regex Parsing Commons
 * Copyright (C) 2009-2021 SonarSource SA
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
import org.sonarsource.analyzer.commons.regex.RegexIssueReporter;
import org.sonarsource.analyzer.commons.regex.RegexParseResult;
import org.sonarsource.analyzer.commons.regex.ast.AutomatonState;
import org.sonarsource.analyzer.commons.regex.ast.Quantifier;
import org.sonarsource.analyzer.commons.regex.ast.RegexBaseVisitor;
import org.sonarsource.analyzer.commons.regex.ast.RepetitionTree;
import org.sonarsource.analyzer.commons.regex.ast.StartState;
import org.sonarsource.analyzer.commons.regex.helpers.RegexReachabilityChecker;
import org.sonarsource.analyzer.commons.regex.helpers.RegexTreeHelper;

public class ReluctantQuantifierWithEmptyContinuationFinder extends RegexBaseVisitor {

  private static final String MESSAGE_FIX = "Fix this reluctant quantifier that will only ever match %s repetition%s.";
  private static final String MESSAGE_UNNECESSARY = "Remove the '?' from this unnecessarily reluctant quantifier.";

  private final RegexIssueReporter.ElementIssue regexElementIssueReporter;
  private AutomatonState endState;

  public ReluctantQuantifierWithEmptyContinuationFinder(RegexIssueReporter.ElementIssue regexElementIssueReporter) {
    this.regexElementIssueReporter = regexElementIssueReporter;
  }

  @Override
  protected void before(RegexParseResult regexParseResult) {
    endState = regexParseResult.getFinalState();
  }

  @Override
  public void visitRepetition(RepetitionTree tree) {
    super.visitRepetition(tree);
    if (tree.getQuantifier().getModifier() == Quantifier.Modifier.RELUCTANT) {
      if (RegexTreeHelper.isAnchoredAtEnd(tree.continuation())) {
        if (RegexTreeHelper.onlyMatchesEmptySuffix(tree.continuation())) {
          regexElementIssueReporter.report(tree, MESSAGE_UNNECESSARY, null, Collections.emptyList());
        }
      } else if (RegexReachabilityChecker.canReachWithoutConsumingInput(new StartState(tree.continuation(), tree.activeFlags()), endState)) {
        int minimumRepetitions = tree.getQuantifier().getMinimumRepetitions();
        regexElementIssueReporter.report(tree, String.format(MESSAGE_FIX, minimumRepetitions, minimumRepetitions == 1 ? "" : "s"), null, Collections.emptyList());
      }
    }
  }
}
