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

  private static final String MESSAGE = "Rework this part of the regex to not match the empty string.";

  private final RegexIssueReporter.ElementIssue regexElementIssueReporter;

  public SuperfluousCurlyBraceFinder(RegexIssueReporter.ElementIssue regexElementIssueReporter) {
    this.regexElementIssueReporter = regexElementIssueReporter;
  }

  @Override
  public void visitRepetition(RepetitionTree tree) {

    Quantifier quantifier = tree.getQuantifier();
    int min = quantifier.getMinimumRepetitions();
    Integer max = quantifier.getMaximumRepetitions();

    if (max != null && max == min) { // is it (xyz){N,N}
      if (min == 1) { // is it (xyz){1}
        super.visitRepetition(tree);
        regexElementIssueReporter.report(quantifier, "Remove this unnecessary quantifier.", null, Collections.emptyList());
      } else if (min == 0) {  // is it (xyz){0}
        regexElementIssueReporter.report(tree, "Remove this unnecessarily quantified expression.", null, Collections.emptyList());
      }
    }
  }
}
