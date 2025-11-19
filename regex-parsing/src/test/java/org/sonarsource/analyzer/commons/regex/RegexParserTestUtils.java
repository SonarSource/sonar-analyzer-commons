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
package org.sonarsource.analyzer.commons.regex;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import javax.annotation.Nullable;
import org.opentest4j.AssertionFailedError;
import org.sonarsource.analyzer.commons.regex.ast.AutomatonState;
import org.sonarsource.analyzer.commons.regex.ast.CharacterClassElementTree;
import org.sonarsource.analyzer.commons.regex.ast.CharacterClassTree;
import org.sonarsource.analyzer.commons.regex.ast.CharacterRangeTree;
import org.sonarsource.analyzer.commons.regex.ast.EndOfRepetitionState;
import org.sonarsource.analyzer.commons.regex.ast.FlagSet;
import org.sonarsource.analyzer.commons.regex.ast.SourceCharacter;
import org.sonarsource.analyzer.commons.regex.ast.CharacterTree;
import org.sonarsource.analyzer.commons.regex.ast.RegexSyntaxElement;
import org.sonarsource.analyzer.commons.regex.ast.RegexToken;
import org.sonarsource.analyzer.commons.regex.ast.RegexTree;
import org.sonarsource.analyzer.commons.regex.ast.RepetitionTree;
import org.sonarsource.analyzer.commons.regex.ast.SequenceTree;
import org.sonarsource.analyzer.commons.regex.java.JavaRegexSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class RegexParserTestUtils {

  private RegexParserTestUtils() {}

  public static RegexTree assertSuccessfulParse(String regex, RegexFeature... features) {
    return assertSuccessfulParse(regex, 0, features);
  }

  public static RegexTree assertSuccessfulParse(String regex, int initialFlags, RegexFeature... features) {
    return assertSuccessfulParseResult(regex, initialFlags, features).getResult();
  }

  public static RegexParseResult assertSuccessfulParseResult(String regex, RegexFeature... features) {
    return assertSuccessfulParseResult(regex, 0, features);
  }

  public static RegexParseResult assertSuccessfulParseResult(String regex, int initialFlags, RegexFeature... features) {
    RegexParseResult result = parseRegex(regex, initialFlags, features);
    if (!result.getSyntaxErrors().isEmpty()) {
      throw new AssertionFailedError("Parsing should complete with no errors.", "no errors", result.getSyntaxErrors());
    }
    assertSame(result.getResult(), result.getStartState().continuation());
    assertSingleEdge(result.getStartState(), result.getResult(), result.getResult().incomingTransitionType());
    AutomatonState finalState = result.getResult().continuation();
    if (result.getResult() instanceof RepetitionTree) {
      assertType(EndOfRepetitionState.class, finalState);
      finalState = finalState.continuation();
    }
    assertSame(result.getFinalState(), finalState);
    assertEquals(AutomatonState.TransitionType.EPSILON, result.getFinalState().incomingTransitionType());
    assertEquals(AutomatonState.TransitionType.EPSILON, result.getStartState().incomingTransitionType());
    assertListSize(0, result.getFinalState().successors());
    assertNull(result.getFinalState().continuation());
    return result;
  }

  public static RegexParseResult parseRegex(String regex, int initialFlags, RegexFeature... features) {
    RegexSource source = makeSource(regex, features);
    RegexParseResult result = new RegexParser(source, new FlagSet(initialFlags)).parse();
    assertEquals(initialFlags, result.getInitialFlags().getMask(), "The initial flags in result should match those passed in.");
    return result;
  }

  public static RegexParseResult parseRegex(String regex, RegexFeature... features) {
    return parseRegex(regex, 0, features);
  }

  public static void assertFailParsing(String regex, String expectedError, RegexFeature... features) {
    RegexParseResult result = parseRegex(regex, 0, features);
    List<SyntaxError> errors = result.getSyntaxErrors();
    if (errors.isEmpty()) {
      throw new AssertionFailedError("Expected error in parsing");
    }
    assertThat(errors.stream().map(SyntaxError::getMessage)).contains(expectedError);
  }

  public static Consumer<AutomatonState> assertEdge(AutomatonState target, AutomatonState.TransitionType type) {
    return actualTarget -> {
      assertSame(target, actualTarget);
      assertEquals(type, actualTarget.incomingTransitionType());
    };
  }

  public static void assertSingleEdge(AutomatonState source, AutomatonState target, AutomatonState.TransitionType type) {
    assertListElements(source.successors(), assertEdge(target, type));
  }

  public static void assertPlainString(String expected, RegexTree regex) {
    SequenceTree sequence = assertType(SequenceTree.class, regex);
    int expectedSize = expected.length();
    if (sequence.getItems().size() != expectedSize) {
      throw new AssertionFailedError("Expected a string of " + expectedSize + " characters, but got " + sequence.getItems());
    }
    for (int i = 0; i < expectedSize; i++) {
      assertCharacter(expected.charAt(i), sequence.getItems().get(i));
    }
  }

  public static void assertPlainString(String expected, String regex, RegexFeature... regexFeatures) {
    assertPlainString(expected, regex, 0, regexFeatures);
  }

  public static void assertPlainString(String expected, String regex, int initialFlags, RegexFeature... regexFeatures) {
    assertPlainString(expected, assertSuccessfulParse(regex, initialFlags, regexFeatures));
  }

  public static void assertCharacter(char expected, @Nullable Boolean expectedEscape, RegexSyntaxElement regex) {
    CharacterTree characterTree = assertType(CharacterTree.class, regex);
    assertKind(RegexTree.Kind.CHARACTER, characterTree);
    assertKind(CharacterClassElementTree.Kind.PLAIN_CHARACTER, characterTree);
    assertEquals(expected, characterTree.codePointOrUnit(), "Code unit should equal character.");
    assertEquals("" + expected, characterTree.characterAsString());
    assertEquals(AutomatonState.TransitionType.CHARACTER, characterTree.incomingTransitionType());
    if (expectedEscape != null) {
      assertEquals(expectedEscape, characterTree.isEscapeSequence());
    }
  }

  public static void assertCharacter(char expected, RegexSyntaxElement regex) {
    assertCharacter(expected, null, regex);
  }

  public static void assertJavaCharacter(int index, char ch, SourceCharacter sourceCharacter) {
    assertEquals(ch, sourceCharacter.getCharacter());
    assertEquals("" + ch, sourceCharacter.getText());
    assertLocation(index, index + 1, sourceCharacter);
  }

  public static void assertToken(int index, String str, RegexToken token) {
    assertEquals(str, token.getText());
    assertLocation(index, index + str.length(), token);
  }

  public static void assertCharacter(char expected, @Nullable Boolean expectedEscape, String regexSource, RegexFeature... features) {
    RegexTree regex = assertSuccessfulParse(regexSource, features);
    assertLocation(0, regexSource.length(), regex);
    assertCharacter(expected, expectedEscape, regex);
  }

  public static void assertCharacter(char expected, String regexSource) {
    assertCharacter(expected, null, regexSource);
  }

  public static CharacterClassElementTree assertCharacterClass(boolean expectNegated, RegexSyntaxElement actual) {
    CharacterClassTree characterClass = assertType(CharacterClassTree.class, actual);
    assertEquals(AutomatonState.TransitionType.CHARACTER, characterClass.incomingTransitionType());
    assertKind(RegexTree.Kind.CHARACTER_CLASS, characterClass);
    assertKind(CharacterClassElementTree.Kind.NESTED_CHARACTER_CLASS, characterClass);
    if (expectNegated) {
      assertTrue(characterClass.isNegated(), "Character class should be negated.");
    } else {
      assertFalse(characterClass.isNegated(), "Character class should not be negated.");
    }
    return characterClass.getContents();
  }

  public static void assertCharacterRange(int expectedLowerBound, int expectedUpperBound, CharacterClassElementTree actual) {
    CharacterRangeTree range = assertType(CharacterRangeTree.class, actual);
    assertKind(CharacterClassElementTree.Kind.CHARACTER_RANGE, range);
    assertEquals(expectedLowerBound, range.getLowerBound().codePointOrUnit(), "Lower bound should be '" + expectedLowerBound + "'.");
    assertEquals(expectedUpperBound, range.getUpperBound().codePointOrUnit(), "Upper bound should be '" + expectedUpperBound + "'.");
  }

  public static <T> T assertType(Class<T> klass, @Nullable Object o) {
    if (o == null) {
      throw new AssertionFailedError("Object should not be null.");
    }
    String actual = o.getClass().getSimpleName();
    String expected = klass.getSimpleName();
    if (!klass.isInstance(o)) {
      throw new AssertionFailedError("Object should have the correct type. ", expected, actual);
    }
    return klass.cast(o);
  }

  public static <T> void assertListSize(int expected, List<T> actual) {
    if (actual.size() != expected) {
      throw new AssertionFailedError("List should have the expected size.", "list of size " + expected, actual);
    }
  }

  @SafeVarargs
  public static <T> void assertListElements(List<? extends T> actual, Consumer<T>... assertions) {
    assertListSize(assertions.length, actual);
    for (int i = 0; i < actual.size(); i++) {
      assertions[i].accept(actual.get(i));
    }
  }

  public static void assertKind(RegexTree.Kind expected, String regex, RegexFeature... features) {
    assertKind(expected, assertSuccessfulParse(regex, features));
  }

  public static void assertKind(RegexTree.Kind expected, RegexTree actual) {
    assertEquals(expected, actual.kind(), "Regex should have kind " + expected);
    assertTrue(actual.is(expected), "`is` should return true when the kinds match.");
    assertTrue(actual.is(RegexTree.Kind.CHARACTER, RegexTree.Kind.DISJUNCTION, expected), "`is` should return true when one of the kinds match.");
  }

  public static void assertKind(CharacterClassElementTree.Kind expected, CharacterClassElementTree actual) {
    assertEquals(expected, actual.characterClassElementKind(), "Regex should have kind " + expected);
    assertTrue(actual.is(expected), "`is` should return true when the kinds match.");
    assertTrue(actual.is(CharacterClassElementTree.Kind.PLAIN_CHARACTER, expected), "`is` should return true when one of the kinds match.");
  }

  public static void assertLocation(int expectedStart, int expectedEnd, RegexSyntaxElement element) {
    assertEquals(expectedStart, element.getRange().getBeginningOffset(), "Element should start at the given index.");
    assertEquals(expectedEnd, element.getRange().getEndingOffset(), "Element should end at the given index.");
  }

  public static RegexSource makeSource(String content, RegexFeature... features) {
    return new JavaRegexSource(content) {
      final Set<RegexFeature> featureSet = new HashSet<>(Arrays.asList(features));

      @Override
      public Set<RegexFeature> features() {
        return featureSet;
      }

      @Override
      public boolean supportsFeature(RegexFeature feature) {
        return featureSet.contains(feature);
      }
    };
  }
}
