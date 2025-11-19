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

import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.sonarsource.analyzer.commons.regex.RegexFeature;
import org.sonarsource.analyzer.commons.regex.RegexParseResult;
import org.sonarsource.analyzer.commons.regex.SyntaxError;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.sonarsource.analyzer.commons.regex.RegexParserTestUtils.assertCharacter;
import static org.sonarsource.analyzer.commons.regex.RegexParserTestUtils.assertEdge;
import static org.sonarsource.analyzer.commons.regex.RegexParserTestUtils.assertFailParsing;
import static org.sonarsource.analyzer.commons.regex.RegexParserTestUtils.assertKind;
import static org.sonarsource.analyzer.commons.regex.RegexParserTestUtils.assertListElements;
import static org.sonarsource.analyzer.commons.regex.RegexParserTestUtils.assertListSize;
import static org.sonarsource.analyzer.commons.regex.RegexParserTestUtils.assertLocation;
import static org.sonarsource.analyzer.commons.regex.RegexParserTestUtils.assertPlainString;
import static org.sonarsource.analyzer.commons.regex.RegexParserTestUtils.assertSingleEdge;
import static org.sonarsource.analyzer.commons.regex.RegexParserTestUtils.assertSuccessfulParse;
import static org.sonarsource.analyzer.commons.regex.RegexParserTestUtils.assertSuccessfulParseResult;
import static org.sonarsource.analyzer.commons.regex.RegexParserTestUtils.assertToken;
import static org.sonarsource.analyzer.commons.regex.RegexParserTestUtils.assertType;
import static org.sonarsource.analyzer.commons.regex.RegexParserTestUtils.parseRegex;

class GroupTreesTest {

  @Test
  void testGroup() {
    RegexTree regex = assertSuccessfulParse("(x)");
    CapturingGroupTree group = assertType(CapturingGroupTree.class, regex);
    assertKind(RegexTree.Kind.CAPTURING_GROUP, group);
    assertThat(group.getName()).as("Group should be unnamed").isEmpty();
    assertNotNull(group.getGroupHeader());
    assertToken(0, "(", group.getGroupHeader());
    assertNotNull(group.getElement());
    assertCharacter('x', group.getElement());
    assertLocation(0, 3, group);
    assertLocation(1, 2, group.getElement());
  }

  @Test
  void testUnfinishedGroup() {
    RegexParseResult result = parseRegex("(x");
    assertEquals(1, result.getSyntaxErrors().size(), "Expected exactly one error.");
    SyntaxError error = result.getSyntaxErrors().get(0);
    assertEquals("Expected ')', but found the end of the regex", error.getMessage(), "Error should have the right message.");
    assertEquals(new IndexRange(2,3), error.range(), "Error should have the right location.");
  }

  @Test
  void testExtraParenthesis() {
    RegexParseResult result = parseRegex("(x))");
    assertEquals(1, result.getSyntaxErrors().size(), "Expected exactly one error.");
    SyntaxError error = result.getSyntaxErrors().get(0);
    assertEquals("Unexpected ')'", error.getMessage(), "Error should have the right message.");
    assertEquals(new IndexRange(3,4), error.range(), "Error should have the right location.");
  }

  @Test
  void testNonCapturing() {
    RegexTree regex = assertSuccessfulParse("(?:x)");
    NonCapturingGroupTree group = assertType(NonCapturingGroupTree.class, regex);
    assertKind(RegexTree.Kind.NON_CAPTURING_GROUP, group);
    RegexTree element = group.getElement();
    assertNotNull(element, "Group should have a body.");
    assertCharacter('x', element);
    assertLocation(0, 5, group);
    assertLocation(3, 4, group.getElement());
  }

  @Test
  void testNonCapturingIncomplete() {
    assertFailParsing("(?", "Expected flag or ':' or ')', but found the end of the regex");
  }

  @Test
  void testFlags() {
    RegexTree regex = assertSuccessfulParse("a (?x:b c) d");
    SequenceTree seq = assertType(SequenceTree.class, regex);
    assertListElements(seq.getItems(),
      first -> assertCharacter('a', first),
      second -> assertCharacter(' ', second),
      third -> {
        NonCapturingGroupTree group = assertType(NonCapturingGroupTree.class, third);
        assertNotNull(group.getGroupHeader());
        assertToken(2, "(?x:", group.getGroupHeader());
        assertEquals(Pattern.COMMENTS, group.getEnabledFlags().getMask());
        RegexTree element = group.getElement();
        assertNotNull(element, "Group should have a body.");
        assertPlainString("bc", element);
      },
      fourth -> assertCharacter(' ', fourth),
      fifth -> assertCharacter('d', fifth)
    );
  }

  @Test
  void testFlags2() {
    RegexTree regex = assertSuccessfulParse("a (?x)b c(?-x) d");
    SequenceTree seq = assertType(SequenceTree.class, regex);
    assertListElements(seq.getItems(),
      first -> assertCharacter('a', first),
      second -> assertCharacter(' ', second),
      third -> {
        NonCapturingGroupTree group = assertType(NonCapturingGroupTree.class, third);
        assertEquals(Pattern.COMMENTS, group.getEnabledFlags().getMask());
        assertNull(group.getElement(), "Group should not have a body.");
      },
      fourth -> assertCharacter('b', fourth),
      fifth -> assertCharacter('c', fifth),
      sixth -> {
        NonCapturingGroupTree group = assertType(NonCapturingGroupTree.class, sixth);
        assertEquals(Pattern.COMMENTS, group.getDisabledFlags().getMask());
        assertNull(group.getElement(), "Group should not have a body.");
      },
      seventh -> assertCharacter(' ', seventh),
      eighth -> assertCharacter('d', eighth)
    );
  }

  @Test
  void testFlags3() {
    RegexTree regex = assertSuccessfulParse("(?dms)");
    NonCapturingGroupTree group = assertType(NonCapturingGroupTree.class, regex);
    assertNull(group.getGroupHeader());
    assertNull(group.getElement());
    assertEquals(Pattern.UNIX_LINES | Pattern.MULTILINE | Pattern.DOTALL, group.getEnabledFlags().getMask());

    FinalState finalState = assertType(FinalState.class, group.continuation());
    assertEquals(AutomatonState.TransitionType.EPSILON, group.incomingTransitionType());
    assertSingleEdge(group, finalState, AutomatonState.TransitionType.EPSILON);
  }

  @Test
  void testNamedGroup() {
    RegexTree regex = assertSuccessfulParse("(?<foo>x)", RegexFeature.JAVA_SYNTAX_GROUP_NAME);
    CapturingGroupTree group = assertType(CapturingGroupTree.class, regex);
    assertNotNull(group.getGroupHeader());
    assertToken(0, "(?<foo>", group.getGroupHeader());
    assertKind(RegexTree.Kind.CAPTURING_GROUP, group);
    assertThat(group.getName()).hasValue("foo");
    assertNotNull(group.getElement());
    assertCharacter('x', group.getElement());
  }

  @Test
  void testIncompleteNamedGroup() {
    assertFailParsing("(?<", "Expected '>', but found the end of the regex", RegexFeature.JAVA_SYNTAX_GROUP_NAME);
  }

  @Test
  void testPositiveLookAhead() {
    RegexTree regex = assertSuccessfulParse("(?=x)");
    LookAroundTree lookAround = assertType(LookAroundTree.class, regex);
    assertKind(RegexTree.Kind.LOOK_AROUND, lookAround);
    assertEquals(LookAroundTree.Polarity.POSITIVE, lookAround.getPolarity());
    assertEquals(LookAroundTree.Direction.AHEAD, lookAround.getDirection());
    assertNotNull(lookAround.getElement());
    RegexTree x = lookAround.getElement();
    assertCharacter('x', x);

    FinalState finalState = assertType(FinalState.class, regex.continuation());
    assertEquals(AutomatonState.TransitionType.EPSILON, lookAround.incomingTransitionType());
    assertListElements(lookAround.successors(),
      assertEdge(x, AutomatonState.TransitionType.CHARACTER),
      assertEdge(finalState, AutomatonState.TransitionType.EPSILON)
    );
    EndOfLookaroundState endOfLookaroundState = assertType(EndOfLookaroundState.class, x.continuation());
    assertSingleEdge(x, endOfLookaroundState, AutomatonState.TransitionType.LOOKAROUND_BACKTRACKING);
    assertSingleEdge(endOfLookaroundState, finalState, AutomatonState.TransitionType.EPSILON);
  }

  @Test
  void testPositiveLookBehind() {
    RegexTree regex = assertSuccessfulParse("(?<=x)");
    LookAroundTree lookAround = assertType(LookAroundTree.class, regex);
    assertKind(RegexTree.Kind.LOOK_AROUND, lookAround);
    assertEquals(LookAroundTree.Polarity.POSITIVE, lookAround.getPolarity());
    assertEquals(LookAroundTree.Direction.BEHIND, lookAround.getDirection());
    assertNotNull(lookAround.getElement());
    RegexTree x = lookAround.getElement();
    assertCharacter('x', x);

    FinalState finalState = assertType(FinalState.class, regex.continuation());
    assertEquals(AutomatonState.TransitionType.EPSILON, lookAround.incomingTransitionType());
    assertListSize(2, lookAround.successors());
    StartOfLookBehindState start = assertType(StartOfLookBehindState.class, lookAround.successors().get(0));
    assertListElements(lookAround.successors(),
      assertEdge(start, AutomatonState.TransitionType.LOOKAROUND_BACKTRACKING),
      assertEdge(finalState, AutomatonState.TransitionType.EPSILON)
    );
    assertSingleEdge(start, x, AutomatonState.TransitionType.CHARACTER);
    EndOfLookaroundState endOfLookaroundState = assertType(EndOfLookaroundState.class, x.continuation());
    assertSingleEdge(x, endOfLookaroundState, AutomatonState.TransitionType.EPSILON);
    assertSingleEdge(endOfLookaroundState, finalState, AutomatonState.TransitionType.EPSILON);
  }

  @Test
  void testNegativeLookAhead() {
    RegexTree regex = assertSuccessfulParse("(?!x)");
    LookAroundTree lookAround = assertType(LookAroundTree.class, regex);
    assertKind(RegexTree.Kind.LOOK_AROUND, lookAround);
    assertEquals(LookAroundTree.Polarity.NEGATIVE, lookAround.getPolarity());
    assertEquals(LookAroundTree.Direction.AHEAD, lookAround.getDirection());
    assertNotNull(lookAround.getElement());
    RegexTree x = lookAround.getElement();
    assertCharacter('x', x);

    FinalState finalState = assertType(FinalState.class, regex.continuation());
    assertEquals(AutomatonState.TransitionType.EPSILON, lookAround.incomingTransitionType());
    assertListSize(2, lookAround.successors());
    NegationState negationState = assertType(NegationState.class, lookAround.successors().get(0));
    assertListElements(lookAround.successors(),
      assertEdge(negationState, AutomatonState.TransitionType.NEGATION),
      assertEdge(finalState, AutomatonState.TransitionType.EPSILON)
    );
    assertSingleEdge(negationState, x, AutomatonState.TransitionType.CHARACTER);
    EndOfLookaroundState endOfLookaroundState = assertType(EndOfLookaroundState.class, x.continuation());
    assertSingleEdge(x, endOfLookaroundState, AutomatonState.TransitionType.LOOKAROUND_BACKTRACKING);
    assertSingleEdge(endOfLookaroundState, finalState, AutomatonState.TransitionType.EPSILON);
  }

  @Test
  void testNegativeLookBehind() {
    RegexTree regex = assertSuccessfulParse("(?<!x)");
    LookAroundTree lookAround = assertType(LookAroundTree.class, regex);
    assertKind(RegexTree.Kind.LOOK_AROUND, lookAround);
    assertEquals(LookAroundTree.Polarity.NEGATIVE, lookAround.getPolarity());
    assertEquals(LookAroundTree.Direction.BEHIND, lookAround.getDirection());
    assertNotNull(lookAround.getElement());
    RegexTree x = lookAround.getElement();
    assertCharacter('x', x);

    FinalState finalState = assertType(FinalState.class, regex.continuation());
    assertEquals(AutomatonState.TransitionType.EPSILON, lookAround.incomingTransitionType());
    assertListSize(2, lookAround.successors());
    StartOfLookBehindState start = assertType(StartOfLookBehindState.class, lookAround.successors().get(0));
    assertListElements(lookAround.successors(),
      assertEdge(start, AutomatonState.TransitionType.LOOKAROUND_BACKTRACKING),
      assertEdge(finalState, AutomatonState.TransitionType.EPSILON)
    );
    assertListSize(1, start.successors());
    NegationState negationState = assertType(NegationState.class, start.successors().get(0));
    assertSingleEdge(start, negationState, AutomatonState.TransitionType.NEGATION);
    assertSingleEdge(negationState, x, AutomatonState.TransitionType.CHARACTER);
    EndOfLookaroundState endOfLookaroundState = assertType(EndOfLookaroundState.class, x.continuation());
    assertSingleEdge(x, endOfLookaroundState, AutomatonState.TransitionType.EPSILON);
    assertSingleEdge(endOfLookaroundState, finalState, AutomatonState.TransitionType.EPSILON);

  }

  @Test
  void testAtomicGroup() {
    RegexTree regex = assertSuccessfulParse("(?>x)", RegexFeature.ATOMIC_GROUP);
    AtomicGroupTree group = assertType(AtomicGroupTree.class, regex);
    assertKind(RegexTree.Kind.ATOMIC_GROUP, group);
    assertNotNull(group.getElement());
    assertCharacter('x', group.getElement());

    // Raise parsing error when atomic group is not provided
    assertFailParsing("(?>x)", "Expected flag or ':' or ')', but found '>'");
  }

  @Test
  void testRecursion() {
    RegexTree regex = assertSuccessfulParse("(?R)", RegexFeature.RECURSION);
    NonCapturingGroupTree recursion = assertType(NonCapturingGroupTree.class, regex);
    assertKind(RegexTree.Kind.NON_CAPTURING_GROUP, recursion);
    assertNull(recursion.getElement());

    // Raise parsing error when recursion is not provided
    assertFailParsing("(?R)", "Expected flag or ':' or ')', but found 'R'");
  }

}
