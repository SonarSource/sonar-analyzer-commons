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
import org.sonarsource.analyzer.commons.regex.ast.DisjunctionTree;
import org.sonarsource.analyzer.commons.regex.ast.GroupTree;
import org.sonarsource.analyzer.commons.regex.ast.RegexBaseVisitor;
import org.sonarsource.analyzer.commons.regex.ast.RegexTree;
import org.sonarsource.analyzer.commons.regex.ast.RepetitionTree;
import org.sonarsource.analyzer.commons.regex.ast.SequenceTree;

public class EmptyStringRepetitionFinder extends RegexBaseVisitor {

  private static final String MESSAGE = "Rework this part of the regex to not match the empty string.";

  private final RegexCheck.ReportRegexTreeMethod reportRegexTreeMethod;

  public EmptyStringRepetitionFinder(RegexCheck.ReportRegexTreeMethod reportRegexTreeMethod) {
    this.reportRegexTreeMethod = reportRegexTreeMethod;
  }

  @Override
  public void visitRepetition(RepetitionTree tree) {
    RegexTree element = tree.getElement();
    if (matchEmptyString(element)) {
      reportRegexTreeMethod.apply(element, MESSAGE, null, Collections.emptyList());
    }
  }

  private boolean matchEmptyString(RegexTree element) {
    switch (element.kind()) {
      case SEQUENCE:
        return ((SequenceTree) element).getItems().stream().allMatch(this::matchEmptyString);
      case DISJUNCTION:
        return ((DisjunctionTree) element).getAlternatives().stream().anyMatch(this::matchEmptyString);
      case REPETITION:
        return ((RepetitionTree) element).getQuantifier().getMinimumRepetitions() == 0;
      case LOOK_AROUND:
      case BOUNDARY:
        return true;
      default:
        if (element instanceof GroupTree) {
          RegexTree nestedElement = ((GroupTree) element).getElement();
          return nestedElement == null || matchEmptyString(nestedElement);
        }
        return false;
    }
  }
}
