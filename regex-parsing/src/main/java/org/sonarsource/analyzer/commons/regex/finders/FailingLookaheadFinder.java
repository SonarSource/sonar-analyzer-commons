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
import org.sonarsource.analyzer.commons.regex.RegexCheck;
import org.sonarsource.analyzer.commons.regex.ast.FinalState;
import org.sonarsource.analyzer.commons.regex.ast.LookAroundTree;
import org.sonarsource.analyzer.commons.regex.ast.RegexBaseVisitor;
import org.sonarsource.analyzer.commons.regex.ast.RegexTree;
import org.sonarsource.analyzer.commons.regex.helpers.RegexTreeHelper;
import org.sonarsource.analyzer.commons.regex.helpers.SubAutomaton;

public class FailingLookaheadFinder extends RegexBaseVisitor {

  private static final String MESSAGE = "Remove or fix this lookahead assertion that can never be true.";

  private final RegexCheck.ReportRegexTreeMethod reportRegexTreeMethod;
  private final FinalState finalState;

  public FailingLookaheadFinder(RegexCheck.ReportRegexTreeMethod reportRegexTreeMethod, FinalState finalState) {
    this.reportRegexTreeMethod = reportRegexTreeMethod;
    this.finalState = finalState;
  }

  @Override
  public void visitLookAround(LookAroundTree tree) {
    if (tree.getDirection() == LookAroundTree.Direction.AHEAD && doesLookaheadContinuationAlwaysFail(tree)) {
      reportRegexTreeMethod.apply(tree, MESSAGE, null, Collections.emptyList());
    }
    super.visitLookAround(tree);
  }

  private boolean doesLookaheadContinuationAlwaysFail(LookAroundTree lookAround) {
    RegexTree lookAroundElement = lookAround.getElement();
    SubAutomaton lookAroundSubAutomaton = new SubAutomaton(lookAroundElement, lookAroundElement.continuation(), true);
    SubAutomaton continuationSubAutomaton = new SubAutomaton(lookAround.continuation(), finalState, true);

    if (lookAround.getPolarity() == LookAroundTree.Polarity.NEGATIVE) {
      return RegexTreeHelper.supersetOf(lookAroundSubAutomaton, continuationSubAutomaton, false);
    }
    return !RegexTreeHelper.intersects(lookAroundSubAutomaton, continuationSubAutomaton, true);
  }
}
