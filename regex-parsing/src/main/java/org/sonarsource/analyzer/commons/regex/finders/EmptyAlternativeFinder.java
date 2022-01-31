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
import java.util.List;
import org.sonarsource.analyzer.commons.regex.RegexIssueReporter;
import org.sonarsource.analyzer.commons.regex.ast.DisjunctionTree;
import org.sonarsource.analyzer.commons.regex.ast.RegexBaseVisitor;
import org.sonarsource.analyzer.commons.regex.ast.RegexTree;
import org.sonarsource.analyzer.commons.regex.ast.SequenceTree;


public class EmptyAlternativeFinder extends RegexBaseVisitor {

  private static final String MESSAGE = "Remove this empty alternative.";

  private final RegexIssueReporter.ElementIssue regexElementIssueReporter;

  public EmptyAlternativeFinder(RegexIssueReporter.ElementIssue regexElementIssueReporter) {
    this.regexElementIssueReporter = regexElementIssueReporter;
  }

  @Override
  public void visitDisjunction(DisjunctionTree tree) {
    List<RegexTree> alternatives = tree.getAlternatives();
    for (int i = 0; i < alternatives.size(); ++i) {
      RegexTree alternative = alternatives.get(i);
      if (alternative.is(RegexTree.Kind.SEQUENCE)
        && ((SequenceTree) alternative).getItems().isEmpty()
        && i < alternatives.size() - 1) {
        regexElementIssueReporter.report(alternative, MESSAGE, null, Collections.emptyList());
      } else {
        super.visit(alternative);
      }
    }
  }
}
