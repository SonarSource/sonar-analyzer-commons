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
package org.sonarsource.analyzer.commons.regex.ast;

import java.util.Arrays;
import java.util.List;
import org.sonarsource.analyzer.commons.regex.RegexFeature;
import org.sonarsource.analyzer.commons.regex.RegexParseResult;
import org.sonarsource.analyzer.commons.regex.RegexSource;

public class RegexBaseVisitor implements RegexVisitor {

  public void visit(RegexTree tree) {
    tree.accept(this);
  }

  public void visitInCharClass(CharacterClassElementTree tree) {
    tree.accept(this);
  }

  private void visit(List<RegexTree> trees) {
    trees.forEach(this::visit);
  }

  private void visitInCharClass(List<CharacterClassElementTree> trees) {
    trees.forEach(this::visitInCharClass);
  }

  @Override
  public void visit(RegexParseResult regexParseResult) {
    if (!regexParseResult.hasSyntaxErrors()) {
      before(regexParseResult);
      visit(regexParseResult.getResult());
      after(regexParseResult);
    }
  }

  /**
   * Override to perform an action before any part of the regex is visited.
   */
  protected void before(RegexParseResult regexParseResult) {
    // does nothing unless overridden
  }

  /**
   * Override to perform an action after the entire regex has been visited.
   */
  protected void after(RegexParseResult regexParseResult) {
    // does nothing unless overridden
  }

  @Override
  public void visitCharacter(CharacterTree tree) {
    // No children to visit
  }

  @Override
  public void visitSequence(SequenceTree tree) {
    visit(tree.getItems());
  }

  @Override
  public void visitDisjunction(DisjunctionTree tree) {
    visit(tree.getAlternatives());
  }

  @Override
  public void visitGroup(GroupTree tree) {
    visit(tree.getElement());
  }

  @Override
  public void visitCapturingGroup(CapturingGroupTree tree) {
    visit(tree.getElement());
  }

  @Override
  public void visitNonCapturingGroup(NonCapturingGroupTree tree) {
    RegexTree element = tree.getElement();
    if (element != null) {
      visit(element);
    }
  }

  @Override
  public void visitAtomicGroup(AtomicGroupTree tree) {
    visit(tree.getElement());
  }

  @Override
  public void visitLookAround(LookAroundTree tree) {
    visit(tree.getElement());
  }

  @Override
  public void visitBackReference(BackReferenceTree tree) {
    // no children to visit
  }

  @Override
  public void visitRepetition(RepetitionTree tree) {
    visit(tree.getElement());
  }

  @Override
  public void visitCharacterClass(CharacterClassTree tree) {
    visitInCharClass(tree.getContents());
  }

  @Override
  public void visitCharacterRange(CharacterRangeTree tree) {
    // No children to visit
  }

  @Override
  public void visitCharacterClassUnion(CharacterClassUnionTree tree) {
    visitInCharClass(tree.getCharacterClasses());
  }

  @Override
  public void visitCharacterClassIntersection(CharacterClassIntersectionTree tree) {
    visitInCharClass(tree.getCharacterClasses());
  }

  @Override
  public void visitDot(DotTree tree) {
    // No children to visit
  }

  @Override
  public void visitEscapedCharacterClass(EscapedCharacterClassTree tree) {
    // No children to visit
  }

  @Override
  public void visitBoundary(BoundaryTree boundaryTree) {
    // no children to visit
  }

  @Override
  public void visitMiscEscapeSequence(MiscEscapeSequenceTree tree) {
    // no children to visit
  }

  public void visitConditionalSubpattern(ConditionalSubpatternTree tree) {
    RegexTree condition = tree.getCondition();
    if (condition.is(RegexTree.Kind.LOOK_AROUND)) {
      visit(condition);
    }
    visit(tree.getYesPattern());
    RegexTree noPattern = tree.getNoPattern();
    if (noPattern != null) {
      visit(noPattern);
    }
  }

  protected static boolean supportsAnyOfFeatures(RegexTree tree, RegexFeature... features) {
    RegexSource source = tree.getSource();
    return Arrays.stream(features).anyMatch(source::supportsFeature);
  }

  /**
   * In some languages backslashes in strings must be escaped explicitly.
   */
  protected static String backslash(RegexTree tree) {
    return supportsAnyOfFeatures(tree, RegexFeature.BACKSLASH_ESCAPING) ? "\\\\" : "\\";
  }
}
