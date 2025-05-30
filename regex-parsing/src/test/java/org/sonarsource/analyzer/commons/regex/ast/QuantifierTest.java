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

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.sonarsource.analyzer.commons.regex.RegexParserTestUtils.assertEdge;
import static org.sonarsource.analyzer.commons.regex.RegexParserTestUtils.assertFailParsing;
import static org.sonarsource.analyzer.commons.regex.RegexParserTestUtils.assertListElements;
import static org.sonarsource.analyzer.commons.regex.RegexParserTestUtils.assertCharacter;
import static org.sonarsource.analyzer.commons.regex.RegexParserTestUtils.assertSingleEdge;
import static org.sonarsource.analyzer.commons.regex.RegexParserTestUtils.assertSuccessfulParse;
import static org.sonarsource.analyzer.commons.regex.RegexParserTestUtils.assertType;
import static org.sonarsource.analyzer.commons.regex.RegexParserTestUtils.parseRegex;

class QuantifierTest {

  @Test
  void testGreedyStar() {
    assertXWithKleeneStar("x*");
  }

  @Test
  void testGreedyPlus() {
    RegexTree regex = assertSuccessfulParse("x+");
    RepetitionTree repetition = assertType(RepetitionTree.class, regex);
    assertCharacter('x', repetition.getElement());
    SimpleQuantifier quantifier = assertType(SimpleQuantifier.class, repetition.getQuantifier());
    assertEquals(SimpleQuantifier.Kind.PLUS, quantifier.getKind(), "Quantifier should be a plus.");
    assertEquals(1, quantifier.getMinimumRepetitions(), "Lower bound should be 1.");
    assertNull(quantifier.getMaximumRepetitions(), "Plus should have no upper bound.");
    assertTrue(quantifier.isOpenEnded(), "Plus should be open ended.");
    assertEquals(Quantifier.Modifier.GREEDY, quantifier.getModifier(), "Quantifier should be greedy.");

    CurlyBraceQuantifierTest.testAutomaton(repetition, false);
  }

  @Test
  void testGreedyQuestionMark() {
    RegexTree regex = assertSuccessfulParse("x?");
    RepetitionTree repetition = assertType(RepetitionTree.class, regex);
    RegexTree x = repetition.getElement();
    assertCharacter('x', x);
    SimpleQuantifier quantifier = assertType(SimpleQuantifier.class, repetition.getQuantifier());
    assertEquals(SimpleQuantifier.Kind.QUESTION_MARK, quantifier.getKind(), "Quantifier should be a question mark.");
    assertEquals(0, quantifier.getMinimumRepetitions(), "Lower bound should be 0.");
    assertEquals(1, quantifier.getMaximumRepetitions(), "The upper bound of a question mark quantifier should be 1.");
    assertFalse(quantifier.isOpenEnded(), "Question mark should not be open ended.");
    assertEquals(Quantifier.Modifier.GREEDY, quantifier.getModifier(), "Quantifier should be greedy.");

    EndOfRepetitionState endOfRep = assertType(EndOfRepetitionState.class, repetition.continuation());
    assertListElements(repetition.successors(),
      assertEdge(x, AutomatonState.TransitionType.CHARACTER),
      assertEdge(endOfRep, AutomatonState.TransitionType.EPSILON)
    );
    assertSingleEdge(x, endOfRep, AutomatonState.TransitionType.EPSILON);
  }

  @Test
  void testReluctantStar() {
    RegexTree regex = assertSuccessfulParse("x*?");
    RepetitionTree repetition = assertType(RepetitionTree.class, regex);
    assertCharacter('x', repetition.getElement());
    SimpleQuantifier quantifier = assertType(SimpleQuantifier.class, repetition.getQuantifier());
    assertEquals(SimpleQuantifier.Kind.STAR, quantifier.getKind(), "Quantifier should be a Kleene star.");
    assertEquals(Quantifier.Modifier.RELUCTANT, quantifier.getModifier(), "Quantifier should be reluctant.");
    assertTrue(repetition.isReluctant());
    assertFalse(repetition.isPossessive());

    testStarAutomaton(repetition, true);
  }

  @Test
  void testPossessiveStar() {
    RegexTree regex = assertSuccessfulParse("x*+", RegexFeature.POSSESSIVE_QUANTIFIER);
    RepetitionTree repetition = assertType(RepetitionTree.class, regex);
    assertCharacter('x', repetition.getElement());
    SimpleQuantifier quantifier = assertType(SimpleQuantifier.class, repetition.getQuantifier());
    assertEquals(SimpleQuantifier.Kind.STAR, quantifier.getKind(), "Quantifier should be a Kleene star.");
    assertEquals(Quantifier.Modifier.POSSESSIVE, quantifier.getModifier(), "Quantifier should be possessive.");
    assertTrue(repetition.isPossessive());
    assertFalse(repetition.isReluctant());

    testStarAutomaton(repetition, false);

    // Raise parsing error when possessive quantifier is not provided
    assertFailParsing("x*+", "Unexpected quantifier '+'");
  }

  @Test
  void testDoubleQuantifier() {
    RegexParseResult result = parseRegex("x**");
    assertEquals(1, result.getSyntaxErrors().size(), "Expected exactly one error.");
    SyntaxError error = result.getSyntaxErrors().get(0);
    assertEquals("Unexpected quantifier '*'", error.getMessage(), "Error should have the right message.");
    assertEquals("*", error.getOffendingSyntaxElement().getText(), "Error should complain about the correct part of the regex.");
    assertEquals(new IndexRange(2,3), error.range(), "Error should have the right location.");
  }

  @Test
  void quotesInRepetition() {
    assertXWithKleeneStar("\\\\Qx\\\\E*");
    assertXWithKleeneStar("x\\\\Q\\\\E*");
  }

  @Test
  void quantifiersWithoutOperand() {
    assertFailParsing("*", "Unexpected quantifier '*'");
    assertFailParsing("+", "Unexpected quantifier '+'");
    assertFailParsing("?", "Unexpected quantifier '?'");
    assertFailParsing("{1,10}", "Unexpected quantifier '{1,10}'");
  }

  private void assertXWithKleeneStar(String regexSource) {
    RegexTree regex = assertSuccessfulParse(regexSource);
    RepetitionTree repetition = assertType(RepetitionTree.class, regex);
    assertCharacter('x', repetition.getElement());
    SimpleQuantifier quantifier = assertType(SimpleQuantifier.class, repetition.getQuantifier());
    assertEquals(SimpleQuantifier.Kind.STAR, quantifier.getKind(), "Quantifier should be a Kleene star.");
    assertEquals(0, quantifier.getMinimumRepetitions(), "Lower bound should be 0.");
    assertNull(quantifier.getMaximumRepetitions(), "Kleene star should have no upper bound.");
    assertTrue(quantifier.isOpenEnded(), "Kleene star should be open ended.");
    assertEquals(Quantifier.Modifier.GREEDY, quantifier.getModifier(), "Quantifier should be greedy.");

    testStarAutomaton(repetition, false);
  }

  private static void testStarAutomaton(RepetitionTree repetition, boolean reluctant) {
    EndOfRepetitionState endOfRep = assertType(EndOfRepetitionState.class, repetition.continuation());
    assertEquals(repetition.activeFlags(), endOfRep.activeFlags());
    FinalState finalState = assertType(FinalState.class, endOfRep.continuation());
    assertSingleEdge(endOfRep, finalState, AutomatonState.TransitionType.EPSILON);
    RegexTree x = repetition.getElement();
    assertEquals(AutomatonState.TransitionType.EPSILON, repetition.incomingTransitionType());
    if (reluctant) {
      assertListElements(repetition.successors(),
        assertEdge(endOfRep, AutomatonState.TransitionType.EPSILON),
        assertEdge(x, AutomatonState.TransitionType.CHARACTER)
      );
    } else {
      assertListElements(repetition.successors(),
        assertEdge(x, AutomatonState.TransitionType.CHARACTER),
        assertEdge(endOfRep, AutomatonState.TransitionType.EPSILON)
      );
    }
    assertSingleEdge(x, repetition, AutomatonState.TransitionType.EPSILON);
  }

}
