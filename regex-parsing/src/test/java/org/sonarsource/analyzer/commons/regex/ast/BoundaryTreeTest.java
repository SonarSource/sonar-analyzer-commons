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

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.sonarsource.analyzer.commons.regex.RegexParserTestUtils.assertFailParsing;
import static org.sonarsource.analyzer.commons.regex.RegexParserTestUtils.assertKind;
import static org.sonarsource.analyzer.commons.regex.RegexParserTestUtils.assertSuccessfulParse;

class BoundaryTreeTest {

  @Test
  void boundaries() {
    assertBoundary("^", BoundaryTree.Type.LINE_START);
    assertBoundary("$", BoundaryTree.Type.LINE_END);
    assertBoundary("\\\\b", BoundaryTree.Type.WORD);
    assertBoundary("\\\\b{o}", BoundaryTree.Type.UNICODE_EXTENDED_GRAPHEME_CLUSTER);
    assertBoundary("\\\\B", BoundaryTree.Type.NON_WORD);
    assertBoundary("\\\\A", BoundaryTree.Type.INPUT_START);
    assertBoundary("\\\\z", BoundaryTree.Type.INPUT_END);
    assertBoundary("\\\\Z", BoundaryTree.Type.INPUT_END_FINAL_TERMINATOR);
    assertBoundary("\\\\G", BoundaryTree.Type.PREVIOUS_MATCH_END);
  }

  @Test
  void failing() {
    assertFailParsing("\\\\b{", "Expected an Unicode extended grapheme cluster, but found the end of the regex");
    assertFailParsing("\\\\b{o", "Expected '}', but found the end of the regex");
  }

  private static void assertBoundary(String regex, BoundaryTree.Type expectedType) {
    RegexTree tree = assertSuccessfulParse(regex);
    assertThat(tree).isInstanceOf(BoundaryTree.class);
    assertKind(RegexTree.Kind.BOUNDARY, tree);

    BoundaryTree boundaryTree = (BoundaryTree) tree;
    assertThat(boundaryTree.type()).isEqualTo(expectedType);
    assertThat(boundaryTree.incomingTransitionType()).isEqualTo(AutomatonState.TransitionType.EPSILON);
  }

}
