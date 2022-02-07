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

import java.util.ArrayList;
import java.util.List;
import org.sonarsource.analyzer.commons.regex.RegexIssueLocation;
import org.sonarsource.analyzer.commons.regex.RegexIssueReporter;
import org.sonarsource.analyzer.commons.regex.RegexParseResult;
import org.sonarsource.analyzer.commons.regex.ast.BackReferenceTree;
import org.sonarsource.analyzer.commons.regex.ast.CharacterClassIntersectionTree;
import org.sonarsource.analyzer.commons.regex.ast.CharacterClassTree;
import org.sonarsource.analyzer.commons.regex.ast.DisjunctionTree;
import org.sonarsource.analyzer.commons.regex.ast.LookAroundTree;
import org.sonarsource.analyzer.commons.regex.ast.NonCapturingGroupTree;
import org.sonarsource.analyzer.commons.regex.ast.RegexBaseVisitor;
import org.sonarsource.analyzer.commons.regex.ast.RegexSyntaxElement;
import org.sonarsource.analyzer.commons.regex.ast.RegexToken;
import org.sonarsource.analyzer.commons.regex.ast.RepetitionTree;
import org.sonarsource.analyzer.commons.regex.ast.SourceCharacter;

public class ComplexRegexFinder extends RegexBaseVisitor {

  private static final String MESSAGE = "Simplify this regular expression to reduce its complexity from %d to the %d allowed.";

  private final RegexIssueReporter.ElementIssue regexElementIssueReporter;
  private final int max;

  private int complexity = 0;
  private int nesting = 1;
  private final List<RegexIssueLocation> components = new ArrayList<>();

  public ComplexRegexFinder(RegexIssueReporter.ElementIssue regexElementIssueReporter, int max) {
    this.regexElementIssueReporter = regexElementIssueReporter;
    this.max = max;
  }

  private void increaseComplexity(RegexSyntaxElement syntaxElement, int increment) {
    complexity += increment;
    String message = "+" + increment;
    if (increment > 1) {
      message += " (incl " + (increment - 1) + " for nesting)";
    }
    components.add(new RegexIssueLocation(syntaxElement, message));
  }

  @Override
  public void visitDisjunction(DisjunctionTree tree) {
    increaseComplexity(tree.getOrOperators().get(0), nesting);
    for (SourceCharacter orOperator : tree.getOrOperators().subList(1, tree.getOrOperators().size())) {
      increaseComplexity(orOperator, 1);
    }
    nesting++;
    super.visitDisjunction(tree);
    nesting--;
  }

  @Override
  public void visitRepetition(RepetitionTree tree) {
    increaseComplexity(tree.getQuantifier(), nesting);
    nesting++;
    super.visitRepetition(tree);
    nesting--;
  }

  // Character classes increase the complexity by only one regardless of nesting because they're not that complex by
  // themselves
  @Override
  public void visitCharacterClass(CharacterClassTree tree) {
    increaseComplexity(tree.getOpeningBracket(), 1);
    nesting++;
    super.visitCharacterClass(tree);
    nesting--;
  }

  // Intersections in character classes are a different matter though
  @Override
  public void visitCharacterClassIntersection(CharacterClassIntersectionTree tree) {
    // Subtract one from nesting because we want to treat [a-z&&0-9] as nesting level 1 and [[a-z&&0-9]otherstuff] as
    // nesting level 2
    increaseComplexity(tree.getAndOperators().get(0), nesting - 1);
    for (RegexToken andOperator : tree.getAndOperators().subList(1, tree.getAndOperators().size())) {
      increaseComplexity(andOperator, 1);
    }
    nesting++;
    super.visitCharacterClassIntersection(tree);
    nesting--;
  }

  // Regular groups, names groups and non-capturing groups without flags don't increase complexity because they don't
  // do anything by themselves. However lookarounds, atomic groups and non-capturing groups with flags do because
  // they're more complicated features
  @Override
  public void visitNonCapturingGroup(NonCapturingGroupTree tree) {
    if (tree.getEnabledFlags().isEmpty() && tree.getDisabledFlags().isEmpty()) {
      super.visitNonCapturingGroup(tree);
    } else {
      if (tree.getGroupHeader() == null) {
        increaseComplexity(tree, nesting);
      } else {
        increaseComplexity(tree.getGroupHeader(), nesting);
      }
      nesting++;
      super.visitNonCapturingGroup(tree);
      nesting--;
    }
  }

  @Override
  public void visitLookAround(LookAroundTree tree) {
    increaseComplexity(tree.getGroupHeader(), nesting);
    nesting++;
    super.visitLookAround(tree);
    nesting--;
  }

  @Override
  public void visitBackReference(BackReferenceTree tree) {
    increaseComplexity(tree, 1);
  }

  @Override
  protected void after(RegexParseResult regexParseResult) {
    if (complexity > max) {
      int cost = complexity - max;
      regexElementIssueReporter.report(regexParseResult.getResult(), String.format(MESSAGE, complexity, max), cost, components);
    }
  }
}
