/*
 * SonarSource Analyzers Regex Parsing Commons
 * Copyright (C) 2009-2025 SonarSource Sàrl
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

import java.util.List;
import org.junit.jupiter.api.Test;
import org.sonarsource.analyzer.commons.regex.RegexFeature;

import static org.assertj.core.api.Assertions.assertThat;
import static org.sonarsource.analyzer.commons.regex.RegexParserTestUtils.assertFailParsing;
import static org.sonarsource.analyzer.commons.regex.RegexParserTestUtils.assertKind;
import static org.sonarsource.analyzer.commons.regex.RegexParserTestUtils.assertSingleEdge;
import static org.sonarsource.analyzer.commons.regex.RegexParserTestUtils.assertSuccessfulParse;
import static org.sonarsource.analyzer.commons.regex.RegexParserTestUtils.assertType;

class CapturingGroupTreeTest {

  @Test
  void java_syntax_named_groups() {
    RegexTree tree = assertSuccessfulParse("((A)(?:N)(B(?<groupC>C)))", RegexFeature.JAVA_SYNTAX_GROUP_NAME);
    assertKind(RegexTree.Kind.CAPTURING_GROUP, tree);
    CapturingGroupTree abc = ((CapturingGroupTree) tree);
    assertThat(abc.getGroupNumber()).isEqualTo(1);
    assertThat(abc.getName()).isEmpty();

    RegexTree abcElement = abc.getElement();
    assertKind(RegexTree.Kind.SEQUENCE, abcElement);
    List<RegexTree> abcItems = ((SequenceTree) abcElement).getItems();
    assertThat(abcItems).hasSize(3);
    assertThat(abcItems.stream().map(RegexTree::kind)).containsExactly(RegexTree.Kind.CAPTURING_GROUP, RegexTree.Kind.NON_CAPTURING_GROUP, RegexTree.Kind.CAPTURING_GROUP);

    CapturingGroupTree a = ((CapturingGroupTree) abcItems.get(0));
    CapturingGroupTree bc = ((CapturingGroupTree) abcItems.get(2));

    assertThat(a.getGroupNumber()).isEqualTo(2);
    assertThat(a.getName()).isEmpty();

    assertThat(bc.getGroupNumber()).isEqualTo(3);
    assertThat(bc.getName()).isEmpty();

    RegexTree bcElement = bc.getElement();
    assertKind(RegexTree.Kind.SEQUENCE, bcElement);
    List<RegexTree> bcItems = ((SequenceTree) bcElement).getItems();
    assertThat(bcItems).hasSize(2);

    assertKind(RegexTree.Kind.CHARACTER, bcItems.get(0));
    assertKind(RegexTree.Kind.CAPTURING_GROUP, bcItems.get(1));

    CapturingGroupTree c = ((CapturingGroupTree) bcItems.get(1));
    assertThat(c.getGroupNumber()).isEqualTo(4);
    assertThat(c.getName()).hasValue("groupC");

    testAutomaton(abc, abcItems, a, bc, bcItems, c);
  }

  @Test
  void dotnet_syntax_named_groups() {
    RegexTree tree = assertSuccessfulParse("(?'groupA'A)(?<groupB>B)", RegexFeature.DOTNET_SYNTAX_GROUP_NAME);
    assertKind(RegexTree.Kind.SEQUENCE, tree);
    List<RegexTree> abItems = ((SequenceTree) tree).getItems();
    assertThat(abItems).hasSize(2);
    assertThat(abItems.stream().map(RegexTree::kind)).containsExactly(RegexTree.Kind.CAPTURING_GROUP, RegexTree.Kind.CAPTURING_GROUP);
    CapturingGroupTree a = ((CapturingGroupTree) abItems.get(0));
    assertThat(a.getGroupNumber()).isEqualTo(1);
    assertThat(a.getName()).hasValue("groupA");
    CapturingGroupTree b = ((CapturingGroupTree) abItems.get(1));
    assertThat(b.getGroupNumber()).isEqualTo(2);
    assertThat(b.getName()).hasValue("groupB");
  }

  @Test
  void python_syntax_named_groups() {
    RegexTree tree = assertSuccessfulParse("(?P<groupA>A)", RegexFeature.PYTHON_SYNTAX_GROUP_NAME);
    assertKind(RegexTree.Kind.CAPTURING_GROUP, tree);
    CapturingGroupTree a = ((CapturingGroupTree) tree);
    assertThat(a.getGroupNumber()).isEqualTo(1);
    assertThat(a.getName()).hasValue("groupA");
  }

  @Test
  void parsingErrorWhenFeatureNotSupported() {
    assertFailParsing("(?<name>)", "Expected flag or ':' or ')', but found '<'");
    assertFailParsing("(?'name')", "Expected flag or ':' or ')', but found '''");
    assertFailParsing("(?P<name>)", "Expected flag or ':' or ')', but found 'P'");
  }

  private void testAutomaton(CapturingGroupTree abc, List<RegexTree> abcItems, CapturingGroupTree a, CapturingGroupTree bc, List<RegexTree> bcItems, CapturingGroupTree c) {
    assertThat(abc.incomingTransitionType()).isEqualTo(AutomatonState.TransitionType.EPSILON);
    assertSingleEdge(abc, abc.getElement(), AutomatonState.TransitionType.EPSILON);
    assertSingleEdge(abc.getElement(), a, AutomatonState.TransitionType.EPSILON);
    assertSingleEdge(a, a.getElement(), AutomatonState.TransitionType.CHARACTER);
    NonCapturingGroupTree n = assertType(NonCapturingGroupTree.class, abcItems.get(1));
    RegexTree nElement = n.getElement();
    assertThat(nElement).isNotNull();
    EndOfCapturingGroupState endOfA = getAndAssertEndOfCapturingGroup(a);
    assertSingleEdge(a.getElement(), endOfA, AutomatonState.TransitionType.EPSILON);
    assertSingleEdge(endOfA, n, AutomatonState.TransitionType.EPSILON);
    assertSingleEdge(n, nElement, AutomatonState.TransitionType.CHARACTER);
    assertSingleEdge(nElement, bc, AutomatonState.TransitionType.EPSILON);
    assertSingleEdge(bc, bc.getElement(), AutomatonState.TransitionType.EPSILON);
    assertSingleEdge(bc.getElement(), bcItems.get(0), AutomatonState.TransitionType.CHARACTER);
    assertSingleEdge(bcItems.get(0), c, AutomatonState.TransitionType.EPSILON);
    assertSingleEdge(c, c.getElement(), AutomatonState.TransitionType.CHARACTER);
    EndOfCapturingGroupState endOfC = getAndAssertEndOfCapturingGroup(c);
    EndOfCapturingGroupState endOfBC = getAndAssertEndOfCapturingGroup(bc);
    EndOfCapturingGroupState endOfABC = getAndAssertEndOfCapturingGroup(abc);
    assertSingleEdge(c.getElement(), endOfC, AutomatonState.TransitionType.EPSILON);
    assertSingleEdge(endOfC, endOfBC, AutomatonState.TransitionType.EPSILON);
    assertSingleEdge(endOfBC, endOfABC, AutomatonState.TransitionType.EPSILON);
    assertSingleEdge(endOfABC, abc.continuation(), AutomatonState.TransitionType.EPSILON);
  }

  private EndOfCapturingGroupState getAndAssertEndOfCapturingGroup(CapturingGroupTree group) {
    EndOfCapturingGroupState endOfGroup = assertType(EndOfCapturingGroupState.class, group.getElement().continuation());
    assertThat(endOfGroup.group()).isSameAs(group);
    return endOfGroup;
  }

}
