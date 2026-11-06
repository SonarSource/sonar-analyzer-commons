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

import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.sonarsource.analyzer.commons.regex.RegexParser;
import org.sonarsource.analyzer.commons.regex.ast.CharacterClassElementTree;
import org.sonarsource.analyzer.commons.regex.ast.FlagSet;
import org.sonarsource.analyzer.commons.regex.ast.IndexRange;
import org.sonarsource.analyzer.commons.regex.ast.PosixCharacterClassElementTree;
import org.sonarsource.analyzer.commons.regex.php.PhpRegexSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class PosixCharacterClassTest {

  @ParameterizedTest
  @ValueSource(strings = {"alnum", "alpha", "ascii", "blank", "cntrl", "digit", "graph", "lower", "print", "punct", "space", "upper", "word", "xdigit"})
  void ascii_membership_and_complements(String property) {
    for (int flags : new int[] {0, Pattern.CASE_INSENSITIVE, Pattern.UNICODE_CHARACTER_CLASS, Pattern.UNICODE_CHARACTER_CLASS | Pattern.CASE_INSENSITIVE}) {
      SimplifiedRegexCharacterClass positive = parse("[[:" + property + ":]]", flags);
      SimplifiedRegexCharacterClass negative = parse("[[:^" + property + ":]]", flags);
      SimplifiedRegexCharacterClass enclosingNegative = parse("[^[:" + property + ":]]", flags);
      SimplifiedRegexCharacterClass doubleNegative = parse("[^[:^" + property + ":]]", flags);
      for (int character = 0; character < 128; character++) {
        boolean expected = contains(property, character, flags);
        SimplifiedRegexCharacterClass singleton = singleton(character);
        assertThat(positive.supersetOf(singleton, false)).as("%s / %s / %s", property, flags, character).isEqualTo(expected);
        assertThat(negative.supersetOf(singleton, false)).isEqualTo(!expected);
        assertThat(enclosingNegative.supersetOf(singleton, false)).isEqualTo(!expected);
        assertThat(doubleNegative.supersetOf(singleton, false)).isEqualTo(expected);
      }
      for (int character : new int[] {128, 255, 256, 0x1000, Character.MAX_CODE_POINT}) {
        assertThat(positive.supersetOf(singleton(character), false)).isFalse();
        boolean exact = flags == 0 || flags == Pattern.CASE_INSENSITIVE || "ascii".equals(property);
        assertThat(negative.supersetOf(singleton(character), false)).isEqualTo(exact);
        assertThat(enclosingNegative.supersetOf(singleton(character), false)).isEqualTo(exact);
      }
    }
  }

  @Test
  void unions_and_ranges() {
    assertThat(parse("[[:digit:]a-f_]", 0).supersetOf(parse("[0-9a-f_]", 0), false)).isTrue();
    assertThat(parse("[a-f_[:digit:]]", 0).supersetOf(parse("[0-9a-f_]", 0), false)).isTrue();
    assertThat(parse("[[:alpha:][:digit:]]", 0).supersetOf(parse("[[:alnum:]]", 0), false)).isTrue();
    assertThat(parse("[^[:digit:]a-f]", 0).supersetOf(parse("[g-z]", 0), false)).isTrue();
    assertThat(parse("[^[:digit:]a-f]", Pattern.UNICODE_CHARACTER_CLASS).supersetOf(parse("[g-z]", 0), false)).isTrue();
    assertThat(parse("[^[:digit:]a-f]", 0).supersetOf(parse("[a-z]", 0), false)).isFalse();
  }

  @Test
  void unknown_membership_never_proves_coverage() {
    for (String expression : new String[] {"[[:<:]]", "[[:>:]]", "[^[:<:]]", "[[:^>:]]"}) {
      SimplifiedRegexCharacterClass unknown = parse(expression, 0);
      assertThat(unknown.isEmpty()).isFalse();
      assertThat(unknown.matchesAnyCharacter()).isFalse();
      assertThat(unknown.supersetOf(singleton('a'), false)).isFalse();
      assertThat(parse("[[:ascii:]]", 0).supersetOf(unknown, false)).isFalse();
      assertThat(unknown.supersetOf(parse("[^x]", 0), false)).isFalse();
    }
    var unknown = new SimplifiedRegexCharacterClass(new PosixCharacterClassElementTree(new PhpRegexSource("", '\''),
      new IndexRange(0, 0), false, "unknown", new FlagSet()));
    assertThat(unknown.isEmpty()).isFalse();
    assertThat(unknown.matchesAnyCharacter()).isFalse();
    assertThat(unknown.supersetOf(parse("[^x]", 0), false)).isFalse();
    assertThat(unknown.supersetOf(parse("[\\s\\S]", 0), false)).isFalse();

    var unicode = parse("[[:digit:]]", Pattern.UNICODE_CHARACTER_CLASS);
    assertThat(unicode.isEmpty()).isFalse();
    assertThat(unicode.matchesAnyCharacter()).isFalse();
    assertThat(parse("[0-9]", 0).supersetOf(unicode, false)).isFalse();
    assertThat(parse("[[:ascii:]]", Pattern.UNICODE_CHARACTER_CLASS).supersetOf(parse("[[:ascii:]]", 0), false)).isTrue();
  }

  private static boolean contains(String property, int c, int flags) {
    boolean lower = c >= 'a' && c <= 'z';
    boolean upper = c >= 'A' && c <= 'Z';
    boolean digit = c >= '0' && c <= '9';
    return switch (property) {
      case "alnum" -> lower || upper || digit;
      case "alpha" -> lower || upper;
      case "ascii" -> true;
      case "blank" -> c == ' ' || c == '\t';
      case "cntrl" -> c < 32 || c == 127;
      case "digit" -> digit;
      case "graph" -> c > 32 && c < 127;
      case "lower" -> lower || ((flags & Pattern.CASE_INSENSITIVE) != 0 && upper);
      case "print" -> c >= 32 && c < 127;
      case "punct" -> c > 32 && c < 127 && !lower && !upper && !digit;
      case "space" -> c == ' ' || "\t\n\u000b\f\r".indexOf(c) >= 0;
      case "upper" -> upper || ((flags & Pattern.CASE_INSENSITIVE) != 0 && lower);
      case "word" -> lower || upper || digit || c == '_';
      case "xdigit" -> digit || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');
      default -> throw new IllegalArgumentException(property);
    };
  }

  private static SimplifiedRegexCharacterClass singleton(int character) {
    var singleton = new SimplifiedRegexCharacterClass();
    singleton.addRange(character, character, mock(CharacterClassElementTree.class));
    return singleton;
  }

  private static SimplifiedRegexCharacterClass parse(String expression, int flags) {
    var result = new RegexParser(new PhpRegexSource(expression, '\''), new FlagSet(flags)).parse();
    assertThat(result.getSyntaxErrors()).isEmpty();
    return SimplifiedRegexCharacterClass.of(result.getResult());
  }
}
