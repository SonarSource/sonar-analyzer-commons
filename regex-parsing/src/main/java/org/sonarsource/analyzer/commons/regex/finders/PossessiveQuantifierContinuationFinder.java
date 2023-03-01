/*
 * SonarSource Analyzers Regex Parsing Commons
 * Copyright (C) 2009-2023 SonarSource SA
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
import org.sonarsource.analyzer.commons.regex.RegexIssueLocation;
import org.sonarsource.analyzer.commons.regex.RegexIssueReporter;
import org.sonarsource.analyzer.commons.regex.ast.AutomatonState;
import org.sonarsource.analyzer.commons.regex.ast.FinalState;
import org.sonarsource.analyzer.commons.regex.ast.Quantifier;
import org.sonarsource.analyzer.commons.regex.ast.RegexSyntaxElement;
import org.sonarsource.analyzer.commons.regex.ast.RepetitionTree;
import org.sonarsource.analyzer.commons.regex.helpers.BranchTrackingVisitor;
import org.sonarsource.analyzer.commons.regex.helpers.RegexTreeHelper;
import org.sonarsource.analyzer.commons.regex.helpers.SubAutomaton;

public class PossessiveQuantifierContinuationFinder extends BranchTrackingVisitor {

  private static final String MESSAGE = "Change this impossible to match sub-pattern that conflicts with the previous possessive quantifier.";

  private final RegexIssueReporter.ElementIssue regexElementIssueReporter;
  private final FinalState finalState;

  public PossessiveQuantifierContinuationFinder(RegexIssueReporter.ElementIssue regexElementIssueReporter, FinalState finalState) {
    this.regexElementIssueReporter = regexElementIssueReporter;
    this.finalState = finalState;
  }

  @Override
  public void visitRepetition(RepetitionTree repetitionTree) {
    AutomatonState continuation = repetitionTree.continuation();
    while(continuation != null && !(continuation instanceof RegexSyntaxElement)) {
      continuation = continuation.continuation();
    }
    if (continuation != null && doesRepetitionContinuationAlwaysFail(repetitionTree)) {
      regexElementIssueReporter.report((RegexSyntaxElement) continuation, MESSAGE, null,
        Collections.singletonList(new RegexIssueLocation(repetitionTree, "Previous possessive repetition")));
    }
    super.visitRepetition(repetitionTree);
  }

  private boolean doesRepetitionContinuationAlwaysFail(RepetitionTree repetitionTree) {
    Quantifier quantifier = repetitionTree.getQuantifier();
    if (!quantifier.isOpenEnded() || quantifier.getModifier() != Quantifier.Modifier.POSSESSIVE) {
      return false;
    }

    SubAutomaton potentialSuperset = new SubAutomaton(repetitionTree.getElement(), repetitionTree.getElement().continuation(), false);
    SubAutomaton potentialSubset = new SubAutomaton(repetitionTree.continuation(), finalState, getPredecessorsRangeOf(repetitionTree), true);
    return RegexTreeHelper.supersetOf(potentialSuperset, potentialSubset, false);
  }
}
