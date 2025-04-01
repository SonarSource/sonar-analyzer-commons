/*
 * SonarSource Analyzers Regex Parsing Commons
 * Copyright (C) 2009-2025 SonarSource SA
 * mailto:info AT sonarsource DOT com
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the Sonar Source-Available License Version 1, as published by SonarSource SA.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the Sonar Source-Available License for more details.
 *
 * You should have received a copy of the Sonar Source-Available License
 * along with this program; if not, see https://sonarsource.com/license/ssal/
 */
package org.sonarsource.analyzer.commons.regex.ast;

import org.sonarsource.analyzer.commons.regex.RegexParseResult;

public interface RegexVisitor {

  void visit(RegexParseResult regexParseResult);

  void visitBackReference(BackReferenceTree tree);

  void visitCharacter(CharacterTree tree);

  void visitSequence(SequenceTree tree);

  void visitDisjunction(DisjunctionTree tree);

  /** Generic for all 4 different kinds of GroupTree(s) */
  void visitGroup(GroupTree tree);

  void visitCapturingGroup(CapturingGroupTree tree);

  void visitNonCapturingGroup(NonCapturingGroupTree tree);

  void visitAtomicGroup(AtomicGroupTree tree);

  void visitLookAround(LookAroundTree tree);

  void visitRepetition(RepetitionTree tree);

  void visitCharacterClass(CharacterClassTree tree);

  void visitCharacterRange(CharacterRangeTree tree);

  void visitCharacterClassUnion(CharacterClassUnionTree tree);

  void visitCharacterClassIntersection(CharacterClassIntersectionTree tree);

  void visitDot(DotTree tree);

  void visitEscapedCharacterClass(EscapedCharacterClassTree tree);

  void visitBoundary(BoundaryTree boundaryTree);

  void visitMiscEscapeSequence(MiscEscapeSequenceTree tree);

  void visitConditionalSubpattern(ConditionalSubpatternTree tree);

}
