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
import org.sonarsource.analyzer.commons.regex.ast.CharacterClassElementTree;
import org.sonarsource.analyzer.commons.regex.ast.CharacterClassTree;
import org.sonarsource.analyzer.commons.regex.ast.RegexBaseVisitor;

import java.util.Collections;

import static org.sonarsource.analyzer.commons.regex.ast.CharacterClassElementTree.Kind.PLAIN_CHARACTER;
import static org.sonarsource.analyzer.commons.regex.ast.CharacterClassElementTree.Kind.UNICODE_CODE_POINT;

public class SingleCharCharacterClassFinder extends RegexBaseVisitor {

  private static final String MESSAGE = "Replace this character class by the character itself.";

  private final RegexIssueReporter.ElementIssue regexElementIssueReporter;

  public SingleCharCharacterClassFinder(RegexIssueReporter.ElementIssue regexElementIssueReporter) {
    this.regexElementIssueReporter = regexElementIssueReporter;
  }

  @Override
  public void visitCharacterClass(CharacterClassTree tree) {
    CharacterClassElementTree charClass = tree.getContents();
    if (!tree.isNegated()) {
      checkElementTree(charClass);
    }
    super.visitCharacterClass(tree);
  }

  private void checkElementTree(CharacterClassElementTree elementTree) {
    if (elementTree.is(PLAIN_CHARACTER, UNICODE_CODE_POINT) && "\\^$*+?.|({[".indexOf(elementTree.getText()) < 0) {
      regexElementIssueReporter.report(elementTree, MESSAGE, null, Collections.emptyList());
    }
  }
}
