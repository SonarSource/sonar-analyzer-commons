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
package org.sonarsource.analyzer.commons.regex.helpers;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Stack;
import org.sonarsource.analyzer.commons.regex.ast.AbstractRegexSyntaxElement;
import org.sonarsource.analyzer.commons.regex.ast.DisjunctionTree;
import org.sonarsource.analyzer.commons.regex.ast.IndexRange;
import org.sonarsource.analyzer.commons.regex.ast.RegexBaseVisitor;
import org.sonarsource.analyzer.commons.regex.ast.RegexTree;
import org.sonarsource.analyzer.commons.regex.ast.RepetitionTree;

/**
 * The BranchTrackingVisitor saves the latest branching nodes as it traverses the tree. This is useful to avoid
 * cycles during Automaton evaluation.
 */
public class BranchTrackingVisitor extends RegexBaseVisitor {
  private final Deque<RegexTree> branchingNodes = new ArrayDeque<>();

  @Override
  public void visitDisjunction(DisjunctionTree tree) {
    branchingNodes.push(tree);
    super.visitDisjunction(tree);
    branchingNodes.pop();
  }

  @Override
  public void visitRepetition(RepetitionTree tree) {
    branchingNodes.push(tree);
    super.visitRepetition(tree);
    branchingNodes.pop();
  }

  /**
   * Return the range of a node's branch. A branch is a group of nodes which are always traversed together by the
   * automaton.
   * @param tree a node
   * @return IndexRange of the node's branch.
   */
  public IndexRange getBranchRangeFor(RegexTree tree) {
    if (branchingNodes.isEmpty()) {
      return IndexRange.inaccessible();
    } else {
      RegexTree closestBranchingNode = branchingNodes.peek();
      if (branchingNodes.peek().is(RegexTree.Kind.REPETITION)) {
        return ((RepetitionTree) closestBranchingNode).getElement().getRange();
      } else {
        return ((DisjunctionTree) closestBranchingNode).getAlternatives().stream()
          .filter(alternative -> alternative.getRange().contains(tree.getRange()))
          .findFirst()
          .map(AbstractRegexSyntaxElement::getRange)
          .orElse(IndexRange.inaccessible());
      }
    }
  }
}
