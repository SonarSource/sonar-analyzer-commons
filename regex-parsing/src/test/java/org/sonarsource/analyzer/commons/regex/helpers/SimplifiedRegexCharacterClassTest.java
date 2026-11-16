/*
 * SonarSource Analyzers Regex Parsing Commons
 * Copyright (C) SonarSource Sàrl
 * mailto:info AT sonarsource DOT com
 *
 * You can redistribute and/or modify this program under the terms of
 * the Sonar Source-Available License Version 1, as published by SonarSource Sàrl.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the Sonar Source-Available License for more details.
 *
 * You should have received a copy of the Sonar Source-Available License
 * along with this program; if not, see https://sonarsource.com/license/ssal/
 */
package org.sonarsource.analyzer.commons.regex.helpers;

import java.util.List;
import java.util.regex.Pattern;
import org.assertj.core.api.AbstractBooleanAssert;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.sonarsource.analyzer.commons.regex.java.JavaRegexSource;
import org.sonarsource.analyzer.commons.regex.php.PhpRegexSource;
import org.sonarsource.analyzer.commons.regex.RegexParseResult;
import org.sonarsource.analyzer.commons.regex.RegexParser;
import org.sonarsource.analyzer.commons.regex.RegexSource;
import org.sonarsource.analyzer.commons.regex.ast.CharacterClassElementTree;
import org.sonarsource.analyzer.commons.regex.ast.CharacterRangeTree;
import org.sonarsource.analyzer.commons.regex.ast.CharacterTree;
import org.sonarsource.analyzer.commons.regex.ast.DotTree;
import org.sonarsource.analyzer.commons.regex.ast.FlagSet;
import org.sonarsource.analyzer.commons.regex.ast.IndexRange;
import org.sonarsource.analyzer.commons.regex.ast.MiscEscapeSequenceTree;
import org.sonarsource.analyzer.commons.regex.ast.RegexSyntaxElement;
import org.sonarsource.analyzer.commons.regex.ast.RegexTree;
import org.sonarsource.analyzer.commons.regex.ast.SequenceTree;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class SimplifiedRegexCharacterClassTest {

  public static final int NO_FLAGS = 0;

  @Test
  void testIntersectionWithTrueAsDefaultAnswer() {
    RegexSource dummySource = new JavaRegexSource("");
    IndexRange dummyRange = new IndexRange(0, 0);
    CharacterClassElementTree dummyTree = new MiscEscapeSequenceTree(dummySource, dummyRange, new FlagSet());

    SimplifiedRegexCharacterClass aToZ = new SimplifiedRegexCharacterClass();
    aToZ.addRange('a', 'z', dummyTree);
    SimplifiedRegexCharacterClass unknown = new SimplifiedRegexCharacterClass();
    unknown.add(dummyTree);
    SimplifiedRegexCharacterClass empty = new SimplifiedRegexCharacterClass();

    assertTrue(aToZ.intersects(unknown, true));
    assertFalse(aToZ.intersects(empty, true));
    assertFalse(unknown.intersects(empty, true));

    assertTrue(unknown.intersects(aToZ, true));
    assertFalse(empty.intersects(aToZ, true));
    assertFalse(empty.intersects(unknown, true));
  }

  @Test
  void intersects_dot() {
    assertIntersects(".", "a", false, NO_FLAGS).isTrue();
    assertIntersects(".", ".", false, NO_FLAGS).isTrue();
    assertIntersects(".", "\0", false, NO_FLAGS).isTrue();
    assertIntersects(".", "\uFFFF", false, NO_FLAGS).isTrue();
    assertIntersects("a", ".", true, NO_FLAGS).isTrue();
    assertIntersects(".", "[a-z]", false, NO_FLAGS).isTrue();
    assertIntersects("[a-z]", ".", false, NO_FLAGS).isTrue();
    assertIntersects("[0-73-9]", ".", false, NO_FLAGS).isTrue();
    assertIntersects("[0-93-57]", ".", false, NO_FLAGS).isTrue();

    // by default [\r\n\u0085\u2028\u2029] excluded from DotTree
    assertIntersects(".", "[\r\n\u0085\u2028\u2029]", false, NO_FLAGS).isFalse();
    assertIntersects(".", "\b", false, NO_FLAGS).isTrue();
    assertIntersects(".", "\f", false, NO_FLAGS).isTrue();
    assertIntersects(".", " ", false, NO_FLAGS).isTrue();
    assertIntersects(".", "\t", false, NO_FLAGS).isTrue();
  }

  @Test
  void intersects_dot_unix_lines() {
    // only \n excluded when UNIX_LINES is set
    assertIntersects(".", "\n", false, Pattern.UNIX_LINES).isFalse();
    assertIntersects(".", "[^a]", false, Pattern.UNIX_LINES).isTrue();
    assertIntersects(".", "\b", false, Pattern.UNIX_LINES).isTrue();
    assertIntersects(".", "\t", false, Pattern.UNIX_LINES).isTrue();
    assertIntersects(".", "\f", false, Pattern.UNIX_LINES).isTrue();
    assertIntersects(".", " ", false, Pattern.UNIX_LINES).isTrue();
    assertIntersects(".", "\r", false, Pattern.UNIX_LINES).isTrue();
    assertIntersects(".", "\u0085", false, Pattern.UNIX_LINES).isTrue();
    assertIntersects(".", "\u2028", false, Pattern.UNIX_LINES).isTrue();
    assertIntersects(".", "\u2029", false, Pattern.UNIX_LINES).isTrue();
    assertIntersects(".", ".", false, Pattern.UNIX_LINES).isTrue();
  }

  @Test
  void intersects_dot_all() {
    // no exclusion and UNIX_LINES is ignored when DOTALL is set
    assertIntersects(".", "\n", false, Pattern.DOTALL).isTrue();
    assertIntersects(".", "[^a]", false, Pattern.DOTALL).isTrue();
    assertIntersects(".", "\n", false, Pattern.UNIX_LINES | Pattern.DOTALL).isTrue();
    assertIntersects(".", "\b", false, Pattern.DOTALL).isTrue();
    assertIntersects(".", "\t", false, Pattern.DOTALL).isTrue();
    assertIntersects(".", "\f", false, Pattern.DOTALL).isTrue();
    assertIntersects(".", " ", false, Pattern.DOTALL).isTrue();
    assertIntersects(".", "\r", false, Pattern.DOTALL).isTrue();
    assertIntersects(".", "\u0085", false, Pattern.DOTALL).isTrue();
    assertIntersects(".", "\u2028", false, Pattern.DOTALL).isTrue();
    assertIntersects(".", "\u2029", false, Pattern.DOTALL).isTrue();
    assertIntersects(".", ".", false, Pattern.DOTALL).isTrue();
  }

  @Test
  void intersects_max_code_point() {
    SimplifiedRegexCharacterClass max = new SimplifiedRegexCharacterClass();
    max.addRange(Character.MAX_CODE_POINT, Character.MAX_CODE_POINT, mock(CharacterClassElementTree.class));

    SimplifiedRegexCharacterClass beforeMax = new SimplifiedRegexCharacterClass();
    beforeMax.addRange(Character.MAX_CODE_POINT - 1, Character.MAX_CODE_POINT - 1, mock(CharacterClassElementTree.class));

    SimplifiedRegexCharacterClass aToMax = new SimplifiedRegexCharacterClass();
    aToMax.addRange('a', Character.MAX_CODE_POINT, mock(CharacterClassElementTree.class));

    SimplifiedRegexCharacterClass dot = new SimplifiedRegexCharacterClass(
      new DotTree(mock(RegexSource.class), mock(IndexRange.class), new FlagSet(Pattern.DOTALL)));

    assertThat(max.intersects(max, false)).isTrue();
    assertThat(aToMax.intersects(aToMax, false)).isTrue();
    assertThat(max.intersects(beforeMax, true)).isFalse();
    assertThat(beforeMax.intersects(max, true)).isFalse();
    assertThat(aToMax.intersects(max, false)).isTrue();
    assertThat(max.intersects(aToMax, false)).isTrue();
    assertThat(dot.intersects(dot, false)).isTrue();
    assertThat(max.intersects(dot, false)).isTrue();
    assertThat(dot.intersects(max, false)).isTrue();
    assertThat(beforeMax.intersects(dot, false)).isTrue();
    assertThat(dot.intersects(beforeMax, false)).isTrue();
  }

  @Test
  void superset_of_max_code_point() {
    SimplifiedRegexCharacterClass max = new SimplifiedRegexCharacterClass();
    max.addRange(Character.MAX_CODE_POINT, Character.MAX_CODE_POINT, mock(CharacterClassElementTree.class));

    SimplifiedRegexCharacterClass beforeMax = new SimplifiedRegexCharacterClass();
    beforeMax.addRange(Character.MAX_CODE_POINT - 1, Character.MAX_CODE_POINT - 1, mock(CharacterClassElementTree.class));

    SimplifiedRegexCharacterClass twoBeforeMax = new SimplifiedRegexCharacterClass();
    twoBeforeMax.addRange(Character.MAX_CODE_POINT - 2, Character.MAX_CODE_POINT - 2, mock(CharacterClassElementTree.class));

    SimplifiedRegexCharacterClass aToMax = new SimplifiedRegexCharacterClass();
    aToMax.addRange('a', Character.MAX_CODE_POINT, mock(CharacterClassElementTree.class));

    SimplifiedRegexCharacterClass dot = new SimplifiedRegexCharacterClass(
      new DotTree(mock(RegexSource.class), mock(IndexRange.class), new FlagSet(Pattern.DOTALL)));

    assertThat(max.supersetOf(max, false)).isTrue();
    assertThat(aToMax.supersetOf(aToMax, false)).isTrue();
    assertThat(max.supersetOf(beforeMax, true)).isFalse();
    assertThat(beforeMax.supersetOf(max, true)).isFalse();
    assertThat(max.supersetOf(twoBeforeMax, true)).isFalse();
    assertThat(twoBeforeMax.supersetOf(max, true)).isFalse();
    assertThat(aToMax.supersetOf(max, false)).isTrue();
    assertThat(max.supersetOf(aToMax, true)).isFalse();
    assertThat(dot.supersetOf(dot, false)).isTrue();
    assertThat(max.supersetOf(dot, true)).isFalse();
    assertThat(dot.supersetOf(max, false)).isTrue();
    assertThat(beforeMax.supersetOf(dot, true)).isFalse();
    assertThat(dot.supersetOf(beforeMax, false)).isTrue();
  }

  @Test
  void intersects_with_utf16() {
    String maxCodePoint = new String(Character.toChars(Character.MAX_CODE_POINT));
    // two characters
    assertThat(maxCodePoint).hasSize(2);
    // but a single code point
    assertThat(maxCodePoint.codePoints().count()).isEqualTo(1);
    // and java Pattern support it
    assertThat(maxCodePoint).matches(".");

    RegexTree result = parseRegex(maxCodePoint, new FlagSet()).getResult();
    assertThat(result).isInstanceOf(CharacterTree.class);
    assertIntersects(".", maxCodePoint, false, NO_FLAGS).isTrue();
  }

  @Test
  void unicode_subsets() {
    int flags = Pattern.UNICODE_CHARACTER_CLASS;
    assertSupersetOf("\\\\d", "[0-9]", false, flags).isTrue();
    assertSupersetOf("\\D", "[0-9]", false, flags).isFalse();

    assertSupersetOf("\\w", "\\D", false, flags).isFalse();
    assertSupersetOf("\\w", "\\D", true, flags).isFalse();
    assertSupersetOf("\\\\W", "[:-@]", false, NO_FLAGS).isTrue();
    assertSupersetOf("\\\\W", "[{-´]", false, NO_FLAGS).isTrue();
    assertSupersetOf("\\\\W", "[{-´]", false, flags).isTrue();

    assertSupersetOf("\\\\s", "[\u0085\u00A0\u1680\u2000\u2028\u202F\u205F\u3000]", false, flags).isTrue();
    assertSupersetOf("\\\\s", "[\u0085\u00A0\u1680\u2000\u2028\u202F\u205F\u3000]", false, NO_FLAGS).isFalse();

    assertSupersetOf("\\S", "[\u0085\u00A0\u1680\u2000\u2028\u202F\u205F\u3000]", false, flags).isFalse();
    assertSupersetOf("\\\\S", "[\u0085\u00A0\u1680\u2000\u2028\u202F\u205F\u3000]", false, NO_FLAGS).isTrue();

    assertSupersetOf("\\\\S", "[\u0086\u00A1\u1681\u200B\u202A\u2030\u2060\u3001]", false, flags).isTrue();
    assertSupersetOf("\\\\S", "[\u0086\u00A1\u1681\u200B\u202A\u2030\u2060\u3001]", false, NO_FLAGS).isTrue();
  }

  @Test
  void empty_is_not_superset_of_something_with_unknown_characters() {
    String emptyCharacterClass = "[^\\\\s\\\\S]";
    String unknownCharacter = "\\\\N{slightly smiling face}";
    assertSupersetOf(emptyCharacterClass, unknownCharacter, true, NO_FLAGS).isFalse();
  }

  @Test
  void superset_of_characters_or_range() {
    assertSupersetOf("a", "a", false, NO_FLAGS).isTrue();
    assertSupersetOf("a", "b", true, NO_FLAGS).isFalse();
    assertSupersetOf("[b-d]", "a", true, NO_FLAGS).isFalse();
    assertSupersetOf("[b-d]", "b", false, NO_FLAGS).isTrue();
    assertSupersetOf("[b-d]", "c", false, NO_FLAGS).isTrue();
    assertSupersetOf("[b-d]", "d", false, NO_FLAGS).isTrue();
    assertSupersetOf("[b-d]", "e", true, NO_FLAGS).isFalse();
    assertSupersetOf("a", "[a]", false, NO_FLAGS).isTrue();
    assertSupersetOf("a", "[ab]", true, NO_FLAGS).isFalse();
    assertSupersetOf("[a-z]", "[b-e]", false, NO_FLAGS).isTrue();
    assertSupersetOf("[a-z]", "[b-e]", false, NO_FLAGS).isTrue();
    assertSupersetOf("[a-d]", "[a-e]", true, NO_FLAGS).isFalse();
    assertSupersetOf("[a-d]", "[b-e]", true, NO_FLAGS).isFalse();
    assertSupersetOf("[b-d]", "[a-d]", true, NO_FLAGS).isFalse();
    assertSupersetOf("[a-c]", "[a-c]", false, NO_FLAGS).isTrue();
    assertSupersetOf("[a-ce-g]", "[a-ce-g]", false, NO_FLAGS).isTrue();
    assertSupersetOf("[a-g]", "[a-de-g]", false, NO_FLAGS).isTrue();
    assertSupersetOf("[a-de-g]", "[a-g]", false, NO_FLAGS).isTrue();
    assertSupersetOf("[b-dg-i]", "[a-dg-i]", true, NO_FLAGS).isFalse();
    assertSupersetOf("[b-dg-i]", "[b-eg-i]", true, NO_FLAGS).isFalse();
    assertSupersetOf("[b-dg-i]", "[b-df-i]", true, NO_FLAGS).isFalse();
    assertSupersetOf("[b-dg-i]", "[b-dg-j]", true, NO_FLAGS).isFalse();
  }

  @Test
  void superset_of_dot_default() {
    assertSupersetOf(".", "a", false, NO_FLAGS).isTrue();
    assertSupersetOf(".", "\0", false, NO_FLAGS).isTrue();
    assertSupersetOf(".", "\uFFFF", false, NO_FLAGS).isTrue();
    assertSupersetOf("a", ".", true, NO_FLAGS).isFalse();
    assertSupersetOf(".", "[a-z]", false, NO_FLAGS).isTrue();
    assertSupersetOf("[a-z]", ".", false, NO_FLAGS).isFalse();

    // by default [\r\n\u0085\u2028\u2029] excluded from DotTree
    assertSupersetOf(".", "[^\r\n\u0085\u2028\u2029]", false, NO_FLAGS).isTrue();
    assertSupersetOf(".", "\r", false, NO_FLAGS).isFalse();
    assertSupersetOf(".", "\n", false, NO_FLAGS).isFalse();
    assertSupersetOf(".", "\u0085", false, NO_FLAGS).isFalse();
    assertSupersetOf(".", "\u2028", false, NO_FLAGS).isFalse();
    assertSupersetOf(".", "\u2029", false, NO_FLAGS).isFalse();
    assertSupersetOf(".", "\b", false, NO_FLAGS).isTrue();
    assertSupersetOf(".", "\f", false, NO_FLAGS).isTrue();
    assertSupersetOf(".", " ", false, NO_FLAGS).isTrue();
    assertSupersetOf(".", "\t", false, NO_FLAGS).isTrue();
  }

  @Test
  void superset_of_dot_unix_lines() {
    // only \n excluded when UNIX_LINES is set
    assertSupersetOf(".", "[^\n]", false, Pattern.UNIX_LINES).isTrue();
    assertSupersetOf(".", "\r", false, Pattern.UNIX_LINES).isTrue();
    assertSupersetOf(".", "\n", false, Pattern.UNIX_LINES).isFalse();
    assertSupersetOf(".", "\u0085", false, Pattern.UNIX_LINES).isTrue();
    assertSupersetOf(".", "\u2028", false, Pattern.UNIX_LINES).isTrue();
    assertSupersetOf(".", "\u2029", false, Pattern.UNIX_LINES).isTrue();
    assertSupersetOf(".", "\b", false, Pattern.UNIX_LINES).isTrue();
    assertSupersetOf(".", "\f", false, Pattern.UNIX_LINES).isTrue();
    assertSupersetOf(".", " ", false, Pattern.UNIX_LINES).isTrue();
    assertSupersetOf(".", "\t", false, Pattern.UNIX_LINES).isTrue();
    assertSupersetOf(".", ".", false, Pattern.UNIX_LINES).isTrue();
  }

  @Test
  void superset_of_dot_all() {
    // no exclusion
    assertSupersetOf(".", "[^a]", false, Pattern.DOTALL).isTrue();
    assertSupersetOf(".", "\r", false, Pattern.DOTALL).isTrue();
    assertSupersetOf(".", "\n", false, Pattern.DOTALL).isTrue();
    assertSupersetOf(".", "\u0085", false, Pattern.DOTALL).isTrue();
    assertSupersetOf(".", "\u2028", false, Pattern.DOTALL).isTrue();
    assertSupersetOf(".", "\u2029", false, Pattern.DOTALL).isTrue();
    assertSupersetOf(".", "\b", false, Pattern.DOTALL).isTrue();
    assertSupersetOf(".", "\f", false, Pattern.DOTALL).isTrue();
    assertSupersetOf(".", " ", false, Pattern.DOTALL).isTrue();
    assertSupersetOf(".", "\t", false, Pattern.DOTALL).isTrue();
    assertSupersetOf(".", ".", false, Pattern.DOTALL).isTrue();
    // UNIX_LINES is ignored when DOTALL is set
    assertSupersetOf(".", "\n", false, Pattern.UNIX_LINES | Pattern.DOTALL).isTrue();
  }

  @Test
  void superset_of_predefined_character_classes() {
    assertSupersetOf("\\\\d", "[0-9]", false, NO_FLAGS).isTrue();
    assertSupersetOf("[0-9]", "\\\\d", false, NO_FLAGS).isTrue();
    assertSupersetOf("\\\\d", "[2-5]", false, NO_FLAGS).isTrue();
    assertSupersetOf("[2-5]", "\\d", false, NO_FLAGS).isFalse();
    assertSupersetOf("[^a-z]", "\\\\d", false, NO_FLAGS).isTrue();
    assertSupersetOf("\\d", "[^a-z]", true, NO_FLAGS).isFalse();
  }

  @Test
  void superset_of_case_insensitive() {
    int flags = Pattern.CASE_INSENSITIVE;
    assertSupersetOf("A", "a", false, flags).isTrue();
    assertSupersetOf("a", "A", false, flags).isTrue();
    assertSupersetOf("[a-z]", "[B-F]", false, flags).isTrue();
    assertSupersetOf("[a-f]", "[B-Z]", true, flags).isFalse();

    assertSupersetOf("[Z-a]", "[A]", false, flags).isTrue();
    assertSupersetOf("[Z-a]", "[z]", false, flags).isTrue();
    assertSupersetOf("[Z-a]", "[Z-a]", false, flags).isTrue();

    assertSupersetOf("[Y-b]", "[A-B]", false, flags).isTrue();
    assertSupersetOf("[Y-b]", "[y-z]", false, flags).isTrue();
    assertSupersetOf("[Y-b]", "[Y-b]", false, flags).isTrue();

    assertSupersetOf("[Z-_]", "[Z]", false, flags).isTrue();
    assertSupersetOf("[Z-_]", "[z]", false, flags).isTrue();
    assertSupersetOf("[_-b]", "[a-b]", false, flags).isTrue();
    assertSupersetOf("[_-b]", "[A-B]", false, flags).isTrue();

    assertSupersetOf("[_-`]", "[`]", false, flags).isTrue();
    assertSupersetOf("[_-`]", "[_]", false, flags).isTrue();

    assertSupersetOf("[6-a]", "[6-Z]", false, flags).isTrue();
    assertSupersetOf("[6-a]", "[a-z]", false, flags).isTrue();

    assertSupersetOf("[6-Q]", "[6-Q]", false, flags).isTrue();
    assertSupersetOf("[6-Q]", "[a-q]", false, flags).isTrue();

    assertSupersetOf("[6-_]", "[6-_]", false, flags).isTrue();
    assertSupersetOf("[6-_]", "[a-z]", false, flags).isTrue();
    assertSupersetOf("[6-_]", "[`]", false, flags).isFalse();

    assertSupersetOf("[Q-k]", "[Q-k]", false, flags).isTrue();
    assertSupersetOf("[Q-k]", "[A-K]", false, flags).isTrue();
    assertSupersetOf("[Q-k]", "[q-z]", false, flags).isTrue();

    flags = Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE;

    assertSupersetOf("[Z-à]", "[Z]", false, flags).isTrue();
    assertSupersetOf("[à-è]", "[è]", false, flags).isTrue();

    assertSupersetOf("[Ü-à]", "[Ü]", false, flags).isTrue();
    assertSupersetOf("[Ü-à]", "[à]", false, flags).isTrue();
    assertSupersetOf("[Ü-à]", "[ü]", false, flags).isTrue();
    assertSupersetOf("[Ü-à]", "[À]", false, flags).isTrue();

    assertSupersetOf("[ß-ý]", "[ß]", false, flags).isTrue();
    assertSupersetOf("[ß-ý]", "[ý]", false, flags).isTrue();
    assertSupersetOf("[ß-ý]", "[Ý]", false, flags).isTrue();

  }

  @Test
  void superset_of_character_classes() {
    int flags = 0;
    assertSupersetOf("A", "a", false, flags).isFalse();
    assertSupersetOf("a", "A", false, flags).isFalse();
    assertSupersetOf("[a-z]", "[B-F]", false, flags).isFalse();

    assertSupersetOf("[Z-a]", "[A]", false, flags).isFalse();
    assertSupersetOf("[Z-a]", "[z]", false, flags).isFalse();
    assertSupersetOf("[Z-a]", "[Z-a]", false, flags).isTrue();

    assertSupersetOf("[Y-b]", "[A-B]", false, flags).isFalse();
    assertSupersetOf("[Y-b]", "[y-z]", false, flags).isFalse();
    assertSupersetOf("[Y-b]", "[Y-b]", false, flags).isTrue();

    assertSupersetOf("[Z-_]", "[Z]", false, flags).isTrue();
    assertSupersetOf("[Z-_]", "[z]", false, flags).isFalse();
    assertSupersetOf("[_-b]", "[a-b]", false, flags).isTrue();
    assertSupersetOf("[_-b]", "[A-B]", false, flags).isFalse();

    assertSupersetOf("[Ü-à]", "[Ü]", false, flags).isTrue();
    assertSupersetOf("[Ü-à]", "[à]", false, flags).isTrue();
    assertSupersetOf("[Ü-à]", "[ü]", false, flags).isFalse();
    assertSupersetOf("[Ü-à]", "[À]", false, flags).isFalse();

    assertSupersetOf("[ß-ý]", "[ß]", false, flags).isTrue();
    assertSupersetOf("[ß-ý]", "[ý]", false, flags).isTrue();
    assertSupersetOf("[ß-ý]", "[Ý]", false, flags).isFalse();
  }

  @Test
  void superset_of_default_answer() {
    int flags = Pattern.UNICODE_CHARACTER_CLASS;
    assertSupersetOf("[0-9]", "\\d", false, flags).isFalse();
    assertSupersetOf("[0-9]", "\\\\d", true, flags).isTrue();
  }

  @Test
  void superset_of_empty_set() {
    SimplifiedRegexCharacterClass empty = new SimplifiedRegexCharacterClass();
    assertThat(empty.supersetOf(empty, true)).isTrue();
    assertThat(empty.supersetOf(empty, false)).isTrue();
  }

  @Test
  void conversion() {
    CharacterTree characterTree = (CharacterTree) parseRegex("a", new FlagSet()).getResult();
    assertThat(SimplifiedRegexCharacterClass.of(characterTree)).isNotNull();

    DotTree dotTree = (DotTree) parseRegex(".", new FlagSet()).getResult();
    assertThat(SimplifiedRegexCharacterClass.of(dotTree)).isNotNull();

    SequenceTree sequenceTree = (SequenceTree) parseRegex("ab", new FlagSet()).getResult();
    assertThat(SimplifiedRegexCharacterClass.of(sequenceTree)).isNull();
  }

  @Test
  void matches_any_character() {
    RegexTree supersetResult = parseRegex(".", new FlagSet(0)).getResult();
    SimplifiedRegexCharacterClass simplifiedRegexCharacterClass = SimplifiedRegexCharacterClass.of(supersetResult);
    assertThat(simplifiedRegexCharacterClass).isNotNull();
    assertThat(simplifiedRegexCharacterClass.matchesAnyCharacter()).isFalse();

    supersetResult = parseRegex(".", new FlagSet(Pattern.DOTALL)).getResult();
    simplifiedRegexCharacterClass = SimplifiedRegexCharacterClass.of(supersetResult);
    assertThat(simplifiedRegexCharacterClass).isNotNull();
    assertThat(simplifiedRegexCharacterClass.matchesAnyCharacter()).isTrue();

    SimplifiedRegexCharacterClass characterClass = new SimplifiedRegexCharacterClass();
    assertThat(characterClass.matchesAnyCharacter()).isFalse();
  }

  @Test
  void find_intersections() {
    SimplifiedRegexCharacterClass first = SimplifiedRegexCharacterClass.of(parseRegex("[0-9]", new FlagSet(0)).getResult());
    SimplifiedRegexCharacterClass second = SimplifiedRegexCharacterClass.of(parseRegex("[4-5]", new FlagSet(0)).getResult());

    assertThat(first).isNotNull();
    List<RegexSyntaxElement> intersections = first.findIntersections(second);
    assertThat(intersections).hasSize(1);

    RegexSyntaxElement regexSyntaxElement = intersections.get(0);
    assertThat(regexSyntaxElement).isInstanceOf(CharacterRangeTree.class);

    CharacterRangeTree characterRangeTree = (CharacterRangeTree) regexSyntaxElement;
    assertThat(characterRangeTree.getLowerBound().codePointOrUnit()).isEqualTo(52);
    assertThat(characterRangeTree.getUpperBound().codePointOrUnit()).isEqualTo(53);
  }

  @Test
  void test_reverse_addRange() {
    RegexSyntaxElement element = mock(RegexSyntaxElement.class);
    SimplifiedRegexCharacterClass characterClass = new SimplifiedRegexCharacterClass();
    characterClass.addRange(1, 2, element);
    characterClass.addRange(2, 3, element);

    SimplifiedRegexCharacterClass characterClassReverse = new SimplifiedRegexCharacterClass();
    characterClassReverse.addRange(2, 3, element);
    characterClassReverse.addRange(1, 2, element);

    assertThat(characterClass.intersects(characterClassReverse, false)).isTrue();
  }

  @ParameterizedTest
  @CsvSource(delimiter = ';', value = {
    "[[:alnum:]];a;true", "[[:alnum:]];Z;true", "[[:alnum:]];5;true", "[[:alnum:]];_;false",
    "[[:alpha:]];q;true", "[[:alpha:]];5;false",
    "[[:ascii:]];\\x00;true", "[[:ascii:]];\\x7f;true", "[[:ascii:]];\\x80;false",
    "[[:blank:]];\\t;true", "[[:blank:]];\\x20;true", "[[:blank:]];\\n;false",
    "[[:cntrl:]];\\x00;true", "[[:cntrl:]];\\x1f;true", "[[:cntrl:]];\\x20;false", "[[:cntrl:]];\\x7f;true",
    "[[:digit:]];0;true", "[[:digit:]];9;true", "[[:digit:]];a;false", "[[:digit:]];/;false", "[[:digit:]];:;false",
    "[[:graph:]];!;true", "[[:graph:]];~;true", "[[:graph:]];\\x20;false", "[[:graph:]];\\x7f;false",
    "[[:print:]];\\x20;true", "[[:print:]];~;true", "[[:print:]];\\x7f;false", "[[:print:]];\\x1f;false",
    "[[:punct:]];!;true", "[[:punct:]];_;true", "[[:punct:]];~;true", "[[:punct:]];a;false", "[[:punct:]];5;false", "[[:punct:]];\\x20;false",
    "[[:space:]];\\x20;true", "[[:space:]];\\t;true", "[[:space:]];\\n;true", "[[:space:]];\\x0b;true", "[[:space:]];\\f;true", "[[:space:]];\\r;true", "[[:space:]];x;false",
    "[[:space:]];\\x08;false",
    "[[:word:]];_;true", "[[:word:]];a;true", "[[:word:]];-;false",
    "[[:xdigit:]];a;true", "[[:xdigit:]];F;true", "[[:xdigit:]];g;false", "[[:xdigit:]];9;true",
    "[[:lower:]];a;true", "[[:lower:]];A;false", "[[:upper:]];A;true", "[[:upper:]];a;false",
    "[[:^digit:]];5;false", "[[:^digit:]];a;true", "[[:^digit:]];\\x80;true", "[[:^digit:]];\\x{10000};true",
    "[[:^ascii:]];a;false", "[[:^ascii:]];\\x80;true",
    "[^[:space:]];\\n;false", "[^[:space:]];x;true",
    "[^[:^space:]];\\n;true", "[^[:^space:]];x;false",
    "[[:alpha:]5];5;true", "[[:alpha:]5];6;false", "[[:digit:]a-c];b;true", "[[:digit:]a-c];d;false"
  })
  void posix_class_membership(String characterClass, String character, boolean expected) {
    assertPhpSupersetOf(characterClass, character, false, NO_FLAGS).isEqualTo(expected);
  }

  @ParameterizedTest
  @CsvSource(delimiter = ';', value = {
    "[[:lower:]];A;true", "[[:lower:]];a;true", "[[:lower:]];5;false",
    "[[:upper:]];a;true", "[[:upper:]];A;true",
    "[[:^lower:]];A;false", "[[:^lower:]];a;false", "[[:^lower:]];5;true",
    "[[:^upper:]];a;false", "[[:^upper:]];-;true",
    "[^[:lower:]];A;false", "[^[:lower:]];-;true",
    "[[:alpha:]];Q;true", "[[:digit:]];Q;false"
  })
  void posix_class_case_insensitive(String characterClass, String character, boolean expected) {
    assertPhpSupersetOf(characterClass, character, false, Pattern.CASE_INSENSITIVE).isEqualTo(expected);
  }

  @ParameterizedTest
  @CsvSource(delimiter = ';', value = {
    "[[:alpha:]];a;true", "[[:alpha:]];\\x{e9};false", "[[:space:]];\\n;true", "[[:digit:]];5;true",
    "[[:^alpha:]];5;true", "[[:^alpha:]];a;false", "[[:^alpha:]];\\x{e9};false",
    "[[:ascii:]];a;true", "[[:ascii:]];\\x{e9};false",
    "[[:^ascii:]];\\x{e9};true", "[[:^ascii:]];a;false",
    "[^[:alpha:]];5;true", "[^[:alpha:]];\\x20;true", "[^[:alpha:]];a;false", "[^[:alpha:]];\\x{e9};false",
    "[^[:^alpha:]];a;true", "[^[:^alpha:]];5;false", "[^[:^alpha:]];\\x{e9};false",
    "[^[:alpha:]x];5;true", "[^[:alpha:]x];x;false", "[^[:alpha:]5];5;false", "[^[:alpha:]\\x{e9}];\\x{e9};false"
  })
  void posix_class_unicode_mode(String characterClass, String character, boolean expected) {
    assertPhpSupersetOf(characterClass, character, false, Pattern.UNICODE_CHARACTER_CLASS).isEqualTo(expected);
  }

  @Test
  void posix_class_unknown_membership_is_neither_empty_nor_universal() {
    // Characters above ASCII are unknown in Unicode character class mode
    assertPhpSupersetOf("[[:alpha:]]", "\\x{e9}", true, Pattern.UNICODE_CHARACTER_CLASS).isFalse();
    assertPhpSupersetOf("[[:alpha:]]", "[[:alpha:]]", false, Pattern.UNICODE_CHARACTER_CLASS).isFalse();
    assertPhpSupersetOf("[[:alpha:]]", "[[:alpha:]]", true, Pattern.UNICODE_CHARACTER_CLASS).isTrue();
    assertPhpSupersetOf("[a-z]", "[[:alpha:]]", false, Pattern.UNICODE_CHARACTER_CLASS).isFalse();
    assertPhpSupersetOf("[[:alpha:]]", "[a-z]", false, Pattern.UNICODE_CHARACTER_CLASS).isTrue();
    assertPhpSupersetOf("[^[:alpha:]]", "5", false, Pattern.UNICODE_CHARACTER_CLASS).isTrue();
    assertPhpSupersetOf("[^[:alpha:]]", "[[:alpha:]]", true, Pattern.UNICODE_CHARACTER_CLASS).isFalse();
    assertPhpSupersetOf("[^[:alpha:]]", "\\x{e9}", true, Pattern.UNICODE_CHARACTER_CLASS).isFalse();
    assertPhpSupersetOf("[^[:^alpha:]]", "a", false, Pattern.UNICODE_CHARACTER_CLASS).isTrue();
    assertThat(posixClass("[^[:alpha:]]", Pattern.UNICODE_CHARACTER_CLASS).matchesAnyCharacter()).isFalse();
    assertThat(posixClass("[^[:alpha:]]", Pattern.UNICODE_CHARACTER_CLASS).intersects(posixClass("\\x{e9}", NO_FLAGS), false)).isFalse();
    assertThat(posixClass("[^[:alpha:]]", Pattern.UNICODE_CHARACTER_CLASS).intersects(posixClass("\\x{e9}", NO_FLAGS), true)).isTrue();
    assertThat(posixClass("[[:alpha:]]", Pattern.UNICODE_CHARACTER_CLASS).isEmpty()).isFalse();
    assertThat(posixClass("[[:alpha:]]", Pattern.UNICODE_CHARACTER_CLASS).matchesAnyCharacter()).isFalse();
  }

  @Test
  void posix_word_boundary_markers_are_unknown() {
    for (String marker : new String[] {"[[:<:]]", "[[:>:]]", "[^[:<:]]"}) {
      assertPhpSupersetOf(marker, "a", false, NO_FLAGS).isFalse();
      assertPhpSupersetOf("a", marker, false, NO_FLAGS).isFalse();
      assertPhpSupersetOf(marker, "[a-z]", false, NO_FLAGS).isFalse();
      assertThat(posixClass(marker, NO_FLAGS).isEmpty()).isFalse();
      assertThat(posixClass(marker, NO_FLAGS).matchesAnyCharacter()).isFalse();
    }
    assertThat(posixClass("[[:<:]]", NO_FLAGS).intersects(posixClass("[a-z]", NO_FLAGS), true)).isTrue();
    assertThat(posixClass("[[:<:]]", NO_FLAGS).intersects(posixClass("[a-z]", NO_FLAGS), false)).isFalse();
  }

  private static AbstractBooleanAssert<?> assertPhpSupersetOf(String superset, String subset, boolean defaultAnswer, int flags) {
    return assertThat(posixClass(superset, flags).supersetOf(posixClass(subset, flags), defaultAnswer));
  }

  private static SimplifiedRegexCharacterClass posixClass(String regex, int flags) {
    RegexParseResult result = new RegexParser(new PhpRegexSource(regex, '\''), new FlagSet(flags)).parse();
    assertThat(result.getSyntaxErrors()).isEmpty();
    return SimplifiedRegexCharacterClass.of(result.getResult());
  }

  private static AbstractBooleanAssert<?> assertIntersects(String regex1, String regex2, boolean defaultAnswer, int flags) {
    FlagSet flagSet = new FlagSet(flags);
    RegexTree tree1 = parseRegex(regex1, flagSet).getResult();
    RegexTree tree2 = parseRegex(regex2, flagSet).getResult();
    SimplifiedRegexCharacterClass characterClass1 = SimplifiedRegexCharacterClass.of(tree1);
    SimplifiedRegexCharacterClass characterClass2 = SimplifiedRegexCharacterClass.of(tree2);
    return assertThat(characterClass1.intersects(characterClass2, defaultAnswer));
  }

  private static AbstractBooleanAssert<?> assertSupersetOf(String superset, String subset, boolean defaultAnswer, int flags) {
    FlagSet flagSet = new FlagSet(flags);
    RegexTree supersetResult = parseRegex(superset, flagSet).getResult();
    RegexTree subsetResult = parseRegex(subset, flagSet).getResult();
    SimplifiedRegexCharacterClass supersetCharacterClass = SimplifiedRegexCharacterClass.of(supersetResult);
    SimplifiedRegexCharacterClass subsetCharacterClass = SimplifiedRegexCharacterClass.of(subsetResult);
    return assertThat(supersetCharacterClass.supersetOf(subsetCharacterClass, defaultAnswer));
  }

  static RegexParseResult parseRegex(String stringLiteral, FlagSet flagSet) {
    RegexParseResult result = new RegexParser(new JavaRegexSource(stringLiteral), flagSet).parse();
    assertThat(result.getSyntaxErrors()).isEmpty();
    return result;
  }
}
