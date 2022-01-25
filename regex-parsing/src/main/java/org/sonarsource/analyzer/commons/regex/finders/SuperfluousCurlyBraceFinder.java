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

import org.sonarsource.analyzer.commons.regex.RegexIssueReporter;
import org.sonarsource.analyzer.commons.regex.ast.Quantifier;
import org.sonarsource.analyzer.commons.regex.ast.RegexBaseVisitor;
import org.sonarsource.analyzer.commons.regex.ast.RepetitionTree;

import java.util.Collections;

public class SuperfluousCurlyBraceFinder extends RegexBaseVisitor {

  public static final String REMOVE_UNNECESSARY_QUANTIFIER = "Remove this unnecessary quantifier.";
  public static final String REMOVE_UNNECESSARILY_QUANTIFIED_EXPRESSION = "Remove this unnecessarily quantified expression.";

  private final RegexIssueReporter.ElementIssue regexElementIssueReporter;

  public SuperfluousCurlyBraceFinder(RegexIssueReporter.ElementIssue regexElementIssueReporter) {
    this.regexElementIssueReporter = regexElementIssueReporter;
  }

  @Override
  public void visitRepetition(RepetitionTree tree) {

    Quantifier quantifier = tree.getQuantifier();
    int min = quantifier.getMinimumRepetitions();
    Integer max = quantifier.getMaximumRepetitions();

    // is it a (xyz){N} ? (or its equivalent (xyz){N,N})
    if (max != null && max == min) {
      // is it a (xyz){1} ?
      if (min == 1) {
        super.visitRepetition(tree);
        regexElementIssueReporter.report(quantifier, REMOVE_UNNECESSARY_QUANTIFIER, null, Collections.emptyList());
        // is it a (xyz){0} ?
      } else if (min == 0) {
        regexElementIssueReporter.report(tree, REMOVE_UNNECESSARILY_QUANTIFIED_EXPRESSION, null, Collections.emptyList());
      }
    }
  }
}
