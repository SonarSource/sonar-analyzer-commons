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
import org.sonarsource.analyzer.commons.regex.RegexFeature;
import org.sonarsource.analyzer.commons.regex.RegexParseResult;
import org.sonarsource.analyzer.commons.regex.SyntaxError;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.sonarsource.analyzer.commons.regex.RegexParserTestUtils.assertEdge;
import static org.sonarsource.analyzer.commons.regex.RegexParserTestUtils.assertKind;
import static org.sonarsource.analyzer.commons.regex.RegexParserTestUtils.assertListElements;
import static org.sonarsource.analyzer.commons.regex.RegexParserTestUtils.assertLocation;
import static org.sonarsource.analyzer.commons.regex.RegexParserTestUtils.assertCharacter;
import static org.sonarsource.analyzer.commons.regex.RegexParserTestUtils.assertSingleEdge;
import static org.sonarsource.analyzer.commons.regex.RegexParserTestUtils.assertSuccessfulParse;
import static org.sonarsource.analyzer.commons.regex.RegexParserTestUtils.assertType;
import static org.sonarsource.analyzer.commons.regex.RegexParserTestUtils.parseRegex;

class CurlyBraceQuantifierTest {

  @Test
  void testCurlyBracedQuantifier() {
    RegexTree regex = assertSuccessfulParse("x{23,42}");
    RepetitionTree repetition = assertType(RepetitionTree.class, regex);
    assertKind(RegexTree.Kind.REPETITION, repetition);
    assertCharacter('x', repetition.getElement());
    CurlyBraceQuantifier quantifier = assertType(CurlyBraceQuantifier.class, repetition.getQuantifier());
    assertNotNull(quantifier.getMinimumRepetitionsToken());
    assertNotNull(quantifier.getCommaToken());
    assertNotNull(quantifier.getMaximumRepetitionsToken());
    assertLocation(2, 4, quantifier.getMinimumRepetitionsToken());
    assertLocation(4, 5, quantifier.getCommaToken());
    assertLocation(5, 7, quantifier.getMaximumRepetitionsToken());
    assertEquals(23, quantifier.getMinimumRepetitions(), "Lower bound should be 23.");
    assertEquals(42, quantifier.getMaximumRepetitions(), "Upper bound should be 42.");
    assertFalse(quantifier.isOpenEnded(), "Quantifier should not be open ended.");
    assertFalse(quantifier.isFixed(), "Quantifier should not be marked as only having a single number.");
    assertEquals(Quantifier.Modifier.GREEDY, quantifier.getModifier(), "Quantifier should be greedy.");

    testAutomaton(repetition, false);
  }

  @Test
  void testCurlyBracedQuantifierWithNoUpperBound() {
    RegexTree regex = assertSuccessfulParse("x{42,}");
    RepetitionTree repetition = assertType(RepetitionTree.class, regex);
    assertKind(RegexTree.Kind.REPETITION, repetition);
    assertCharacter('x', repetition.getElement());
    CurlyBraceQuantifier quantifier = assertType(CurlyBraceQuantifier.class, repetition.getQuantifier());
    assertEquals(42, quantifier.getMinimumRepetitions(), "Lower bound should be 42.");
    assertNull(quantifier.getMaximumRepetitions(), "Quantifier should be open ended.");
    assertTrue(quantifier.isOpenEnded(), "Quantifier should be open ended.");
    assertFalse(quantifier.isFixed(), "Quantifier should not be marked as only having a single number.");
    assertEquals(Quantifier.Modifier.GREEDY, quantifier.getModifier(), "Quantifier should be greedy.");

    testAutomaton(repetition, false);
  }

  @Test
  void testFixedCurlyBracedQuantifier() {
    RegexTree regex = assertSuccessfulParse("x{42}");
    RepetitionTree repetition = assertType(RepetitionTree.class, regex);
    assertCharacter('x', repetition.getElement());
    CurlyBraceQuantifier quantifier = assertType(CurlyBraceQuantifier.class, repetition.getQuantifier());
    assertEquals(42, quantifier.getMinimumRepetitions(), "Lower bound should be 42.");
    assertEquals(42, quantifier.getMaximumRepetitions(), "Upper bound should be the same as lower bound.");
    assertFalse(quantifier.isOpenEnded(), "Quantifier should not be open ended.");
    assertTrue(quantifier.isFixed(), "Quantifier should be marked as only having a single number.");
    assertEquals(Quantifier.Modifier.GREEDY, quantifier.getModifier(), "Quantifier should be greedy.");

    testAutomaton(repetition, false);
  }

  @Test
  void testReluctantCurlyBracedQuantifier() {
    RegexTree regex = assertSuccessfulParse("x{23,42}?");
    RepetitionTree repetition = assertType(RepetitionTree.class, regex);
    assertCharacter('x', repetition.getElement());
    CurlyBraceQuantifier quantifier = assertType(CurlyBraceQuantifier.class, repetition.getQuantifier());
    assertEquals(23, quantifier.getMinimumRepetitions(), "Lower bound should be 23.");
    assertEquals(42, quantifier.getMaximumRepetitions(), "Upper bound should be 42.");
    assertEquals(Quantifier.Modifier.RELUCTANT, quantifier.getModifier(), "Quantifier should be reluctant.");

    testAutomaton(repetition, true);
  }

  @Test
  void testPossessiveCurlyBracedQuantifier() {
    RegexTree regex = assertSuccessfulParse("x{23,42}+", RegexFeature.POSSESSIVE_QUANTIFIER);
    RepetitionTree repetition = assertType(RepetitionTree.class, regex);
    assertCharacter('x', repetition.getElement());
    CurlyBraceQuantifier quantifier = assertType(CurlyBraceQuantifier.class, repetition.getQuantifier());
    assertEquals(23, quantifier.getMinimumRepetitions(), "Lower bound should be 23.");
    assertEquals(42, quantifier.getMaximumRepetitions(), "Upper bound should be 42.");
    assertEquals(Quantifier.Modifier.POSSESSIVE, quantifier.getModifier(), "Quantifier should be possessive.");

    testAutomaton(repetition, false);
  }

  @Test
  void testOneOneCurlyBracedQuantifier() {
    RegexTree regex = assertSuccessfulParse("x{1,1}");
    RepetitionTree repetition = assertType(RepetitionTree.class, regex);
    RegexTree x = repetition.getElement();
    assertCharacter('x', x);
    CurlyBraceQuantifier quantifier = assertType(CurlyBraceQuantifier.class, repetition.getQuantifier());
    assertEquals(1, quantifier.getMinimumRepetitions(), "Lower bound should be 1.");
    assertEquals(1, quantifier.getMaximumRepetitions(), "Upper bound should be 1.");
    assertEquals(Quantifier.Modifier.GREEDY, quantifier.getModifier(), "Quantifier should be possessive.");

    EndOfRepetitionState endOfRep = assertType(EndOfRepetitionState.class, repetition.continuation());
    assertEquals(AutomatonState.TransitionType.EPSILON, repetition.incomingTransitionType());
    assertSingleEdge(repetition, x, AutomatonState.TransitionType.CHARACTER);
    assertSingleEdge(x, endOfRep, AutomatonState.TransitionType.EPSILON);
  }

  @Test
  void testZeroZeroCurlyBracedQuantifier() {
    RegexTree regex = assertSuccessfulParse("x{0,0}");
    RepetitionTree repetition = assertType(RepetitionTree.class, regex);
    RegexTree x = repetition.getElement();
    assertCharacter('x', x);
    CurlyBraceQuantifier quantifier = assertType(CurlyBraceQuantifier.class, repetition.getQuantifier());
    assertEquals(0, quantifier.getMinimumRepetitions(), "Lower bound should be 0.");
    assertEquals(0, quantifier.getMaximumRepetitions(), "Upper bound should be 0.");
    assertEquals(Quantifier.Modifier.GREEDY, quantifier.getModifier(), "Quantifier should be possessive.");

    EndOfRepetitionState endOfRep = assertType(EndOfRepetitionState.class, repetition.continuation());
    assertEquals(AutomatonState.TransitionType.EPSILON, repetition.incomingTransitionType());
    assertSingleEdge(repetition, endOfRep, AutomatonState.TransitionType.EPSILON);
  }

  @Test
  void testCurlyBracedQuantifierWithNonNumber() {
    RegexParseResult result = parseRegex("x{a}");
    assertEquals(1, result.getSyntaxErrors().size(), "Expected exactly one error.");
    SyntaxError error = result.getSyntaxErrors().get(0);
    assertEquals("Expected integer, but found 'a'", error.getMessage(), "Error should have the right message.");
    assertEquals("a", error.getOffendingSyntaxElement().getText(), "Error should complain about the correct part of the regex.");
    assertEquals(new IndexRange(2, 3), error.range(), "Error should have the right location.");
  }

  @Test
  void testCurlyBracedQuantifierWithJunkAfterNumber() {
    RegexParseResult result = parseRegex("x{1a}");
    assertEquals(1, result.getSyntaxErrors().size(), "Expected exactly one error.");
    SyntaxError error = result.getSyntaxErrors().get(0);
    assertEquals("Expected ',' or '}', but found 'a'", error.getMessage(), "Error should have the right message.");
    assertEquals("a", error.getOffendingSyntaxElement().getText(), "Error should complain about the correct part of the regex.");
    assertEquals(new IndexRange(3, 4), error.range(), "Error should have the right location.");
  }

  @Test
  void testCurlyBracedQuantifierWithJunkAfterComma() {
    RegexParseResult result = parseRegex("x{1,a}");
    assertEquals(1, result.getSyntaxErrors().size(), "Expected exactly one error.");
    SyntaxError error = result.getSyntaxErrors().get(0);
    assertEquals("Expected integer or '}', but found 'a'", error.getMessage(), "Error should have the right message.");
    assertEquals("a", error.getOffendingSyntaxElement().getText(), "Error should complain about the correct part of the regex.");
    assertEquals(new IndexRange(4, 5), error.range(), "Error should have the right location.");
  }

  @Test
  void testCurlyBracedQuantifierWithJunkAfterSecondNumber() {
    RegexParseResult result = parseRegex("x{1,2a}");
    assertEquals(1, result.getSyntaxErrors().size(), "Expected exactly one error.");
    SyntaxError error = result.getSyntaxErrors().get(0);
    assertEquals("Expected '}', but found 'a'", error.getMessage(), "Error should have the right message.");
    assertEquals("a", error.getOffendingSyntaxElement().getText(), "Error should complain about the correct part of the regex.");
    assertEquals(new IndexRange(5, 6), error.range(), "Error should have the right location.");
  }

  @Test
  void testCurlyBracedQuantifierWithMissingClosingBrace() {
    RegexParseResult result = parseRegex("x{1,2");
    assertEquals(1, result.getSyntaxErrors().size(), "Expected exactly one error.");
    SyntaxError error = result.getSyntaxErrors().get(0);
    assertEquals("Expected '}', but found the end of the regex", error.getMessage(), "Error should have the right message.");
    assertEquals("", error.getOffendingSyntaxElement().getText(), "Error should complain about the correct part of the regex.");
    assertEquals(new IndexRange(5, 6), error.range(), "Error should have the right location.");
  }

  @Test
  void testCurlyBracedQuantifierWithoutOperand() {
    RegexParseResult result = parseRegex("{1,2}");
    assertEquals(1, result.getSyntaxErrors().size(), "Expected exactly one error.");
    SyntaxError error = result.getSyntaxErrors().get(0);
    assertEquals("Unexpected quantifier '{1,2}'", error.getMessage(), "Error should have the right message.");
    assertEquals("{1,2}", error.getOffendingSyntaxElement().getText(), "Error should complain about the correct part of the regex.");
    assertEquals(new IndexRange(0, 5), error.range(), "Error should have the right location.");
  }

  @Test
  void testCurlyBracedQuantifierWithoutOperandInGroup() {
    RegexParseResult result = parseRegex("({1,2})");
    assertEquals(1, result.getSyntaxErrors().size(), "Expected exactly one error.");
    SyntaxError error = result.getSyntaxErrors().get(0);
    assertEquals("Unexpected quantifier '{1,2}'", error.getMessage(), "Error should have the right message.");
    assertEquals(error.getMessage(), error.toString(), "SyntaxError.toString() should equal the error message.");
    assertEquals("{1,2}", error.getOffendingSyntaxElement().getText(), "Error should complain about the correct part of the regex.");
    assertEquals(new IndexRange(1, 6), error.range(), "Error should have the right location.");
  }

  @Test
  void testCurlyBraceWithUnescapedCurlyBraceFeature() {
    assertKind(RegexTree.Kind.REPETITION, "x{1}", RegexFeature.UNESCAPED_CURLY_BRACKET);
    assertKind(RegexTree.Kind.REPETITION, "x{1,}", RegexFeature.UNESCAPED_CURLY_BRACKET);
    assertKind(RegexTree.Kind.REPETITION, "x{1,2}", RegexFeature.UNESCAPED_CURLY_BRACKET);
    assertKind(RegexTree.Kind.REPETITION, "x{12}", RegexFeature.UNESCAPED_CURLY_BRACKET);
    assertKind(RegexTree.Kind.REPETITION, "x{12,}", RegexFeature.UNESCAPED_CURLY_BRACKET);
    assertKind(RegexTree.Kind.REPETITION, "x{12,13}", RegexFeature.UNESCAPED_CURLY_BRACKET);
  }

  @Test
  void onlyUpperBoundQuantifier() {
    RegexTree tree = assertSuccessfulParse("x{,1}", RegexFeature.UNESCAPED_CURLY_BRACKET, RegexFeature.ONLY_UPPER_BOUND_QUANTIFIER);
    RepetitionTree repetition = assertType(RepetitionTree.class, tree);
    CurlyBraceQuantifier quantifier = assertType(CurlyBraceQuantifier.class, repetition.getQuantifier());
    assertNull(quantifier.getMinimumRepetitionsToken());
    assertNotNull(quantifier.getCommaToken());
    assertNotNull(quantifier.getMaximumRepetitionsToken());
    assertEquals(0, quantifier.getMinimumRepetitions());
    assertEquals(1, quantifier.getMaximumRepetitions());
  }

  static void testAutomaton(RepetitionTree repetition, boolean reluctant) {
    RegexTree x = repetition.getElement();
    EndOfRepetitionState endOfRep = assertType(EndOfRepetitionState.class, repetition.continuation());
    assertEquals(repetition.activeFlags(), endOfRep.activeFlags());
    FinalState finalState = assertType(FinalState.class, endOfRep.continuation());
    assertSingleEdge(endOfRep, finalState, AutomatonState.TransitionType.EPSILON);
    assertEquals(AutomatonState.TransitionType.EPSILON, repetition.incomingTransitionType());
    assertSingleEdge(repetition, x, AutomatonState.TransitionType.CHARACTER);
    BranchState branch = assertType(BranchState.class, x.continuation());
    assertSingleEdge(x, branch, AutomatonState.TransitionType.EPSILON);
    assertSame(endOfRep, branch.continuation());
    if (reluctant) {
      assertListElements(branch.successors(),
        assertEdge(endOfRep, AutomatonState.TransitionType.EPSILON),
        assertEdge(repetition, AutomatonState.TransitionType.EPSILON)
      );
    } else {
      assertListElements(branch.successors(),
        assertEdge(repetition, AutomatonState.TransitionType.EPSILON),
        assertEdge(endOfRep, AutomatonState.TransitionType.EPSILON)
      );
    }
  }
}
