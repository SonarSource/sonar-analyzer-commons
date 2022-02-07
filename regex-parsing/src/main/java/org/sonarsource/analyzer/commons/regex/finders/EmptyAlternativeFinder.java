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
import java.util.List;
import org.sonarsource.analyzer.commons.regex.RegexIssueReporter;
import org.sonarsource.analyzer.commons.regex.ast.DisjunctionTree;
import org.sonarsource.analyzer.commons.regex.ast.GroupTree;
import org.sonarsource.analyzer.commons.regex.ast.RegexBaseVisitor;
import org.sonarsource.analyzer.commons.regex.ast.RegexTree;
import org.sonarsource.analyzer.commons.regex.ast.SequenceTree;
import org.sonarsource.analyzer.commons.regex.ast.SourceCharacter;


public class EmptyAlternativeFinder extends RegexBaseVisitor {

  private static final String MESSAGE = "Remove this empty alternative.";

  private final RegexIssueReporter.ElementIssue regexElementIssueReporter;

  private int nestedGroupLevel = 0;

  public EmptyAlternativeFinder(RegexIssueReporter.ElementIssue regexElementIssueReporter) {
    this.regexElementIssueReporter = regexElementIssueReporter;
  }

  @Override
  public void visitGroup(GroupTree tree) {
    nestedGroupLevel++;
    super.visitGroup(tree);
    nestedGroupLevel--;
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

        if (nestedGroupLevel == 0 || (0 < i && i < nAlternatives - 1)) {
          SourceCharacter orOperator = tree.getOrOperators().get(i < nAlternatives - 1 ? i : (i - 1));
          regexElementIssueReporter.report(orOperator, MESSAGE, null, Collections.emptyList());
        }
      }
    }

    if (nestedGroupLevel > 0 && firstIsEmpty && lastIsEmpty) {
      regexElementIssueReporter.report(tree.getOrOperators().get(0), MESSAGE, null, Collections.emptyList());
    }

    super.visitDisjunction(tree);
  }

  private static boolean isEmptyAlternative(RegexTree alternative) {
    return alternative.is(RegexTree.Kind.SEQUENCE) && ((SequenceTree) alternative).getItems().isEmpty();
  }
}
