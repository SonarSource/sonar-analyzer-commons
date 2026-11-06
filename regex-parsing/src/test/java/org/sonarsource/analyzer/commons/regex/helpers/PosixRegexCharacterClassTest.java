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
import org.junit.jupiter.params.provider.CsvSource;
import org.sonarsource.analyzer.commons.regex.RegexParser;
import org.sonarsource.analyzer.commons.regex.ast.CharacterTree;
import org.sonarsource.analyzer.commons.regex.ast.FlagSet;
import org.sonarsource.analyzer.commons.regex.ast.IndexRange;
import org.sonarsource.analyzer.commons.regex.ast.PosixCharacterClassElementTree;
import org.sonarsource.analyzer.commons.regex.php.PhpRegexSource;

import static org.assertj.core.api.Assertions.assertThat;

class PosixRegexCharacterClassTest {

  @ParameterizedTest
  @CsvSource({
    "alnum, [A-Za-z0-9]",
    "alpha, [A-Za-z]",
    "ascii, [\\x00-\\x7f]",
    "blank, [ \\t]",
    "cntrl, [\\x00-\\x1f\\x7f]",
    "digit, [0-9]",
    "graph, [\\x21-\\x7e]",
    "lower, [a-z]",
    "print, [\\x20-\\x7e]",
    "punct, [!-/:-@\\[-`{-~]",
    "space, [ \\t\\n\\x0b\\f\\r]",
    "upper, [A-Z]",
    "word, [A-Za-z0-9_]",
    "xdigit, [A-Fa-f0-9]"
  })
  void ascii_membership_and_complements(String property, String expectedClass) {
    for (int flags : new int[] {0, Pattern.CASE_INSENSITIVE, Pattern.UNICODE_CHARACTER_CLASS,
      Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CHARACTER_CLASS}) {
      Pattern expected = Pattern.compile(expectedClass, flags & Pattern.CASE_INSENSITIVE);
      for (boolean negated : new boolean[] {false, true}) {
        for (boolean outerNegated : new boolean[] {false, true}) {
          String regex = "[" + (outerNegated ? "^" : "") + "[:" + (negated ? "^" : "") + property + ":]]";
          SimplifiedRegexCharacterClass actual = parse(regex, flags);
          assertThat(actual.isEmpty()).as(regex).isFalse();
          assertThat(actual.matchesAnyCharacter()).as(regex).isFalse();
          for (int codePoint = 0; codePoint < 128; codePoint++) {
            boolean matches = expected.matcher(Character.toString(codePoint)).matches() ^ negated ^ outerNegated;
            assertThat(actual.supersetOf(singleCharacter(codePoint), false))
              .as("%s with flags %d at %d", regex, flags, codePoint).isEqualTo(matches);
          }
          for (int codePoint : new int[] {128, 233, 0x1000, Character.MAX_CODE_POINT}) {
            boolean exact = (flags & Pattern.UNICODE_CHARACTER_CLASS) == 0 || "ascii".equals(property);
            assertThat(actual.supersetOf(singleCharacter(codePoint), false))
              .as("%s with flags %d at %d", regex, flags, codePoint).isEqualTo(exact && (negated ^ outerNegated));
          }
        }
      }
    }
  }

  @ParameterizedTest
  @CsvSource({"unknown", "<", ">"})
  void unknown_classes_are_neither_empty_nor_universal(String property) {
    PhpRegexSource source = new PhpRegexSource("", '\'');
    for (boolean negated : new boolean[] {false, true}) {
      SimplifiedRegexCharacterClass unknown = new SimplifiedRegexCharacterClass(
        new PosixCharacterClassElementTree(source, new IndexRange(0, 0), negated, property, new FlagSet()));
      assertThat(unknown.isEmpty()).isFalse();
      assertThat(unknown.matchesAnyCharacter()).isFalse();
      assertThat(unknown.supersetOf(singleCharacter(Character.MAX_CODE_POINT), false)).isFalse();
      assertThat(singleCharacter('a').supersetOf(unknown, false)).isFalse();
    }
  }

  @Test
  void unicode_membership_is_uncertain_except_for_ascii() {
    int flags = Pattern.UNICODE_CHARACTER_CLASS;
    assertThat(parse("[\\x00-\\x7f]", flags).supersetOf(parse("[[:ascii:]]", flags), false)).isTrue();
    assertThat(parse("[^\\x00-\\x7f]", flags).supersetOf(parse("[[:^ascii:]]", flags), false)).isTrue();
    assertThat(parse("[0-9]", flags).supersetOf(parse("[[:digit:]]", flags), false)).isFalse();
    for (String regex : new String[] {"[[:digit:][:^digit:]]", "[^[:digit:][:^digit:]]"}) {
      SimplifiedRegexCharacterClass uncertain = parse(regex, flags);
      assertThat(uncertain.isEmpty()).isFalse();
      assertThat(uncertain.matchesAnyCharacter()).isFalse();
      assertThat(uncertain.supersetOf(singleCharacter(233), false)).isFalse();
    }
  }

  private static SimplifiedRegexCharacterClass parse(String regex, int flags) {
    var result = new RegexParser(new PhpRegexSource(regex, '\''), new FlagSet(flags)).parse();
    assertThat(result.getSyntaxErrors()).isEmpty();
    return SimplifiedRegexCharacterClass.of(result.getResult());
  }

  private static SimplifiedRegexCharacterClass singleCharacter(int codePoint) {
    return new SimplifiedRegexCharacterClass(new CharacterTree(new PhpRegexSource("", '\''), new IndexRange(0, 0), codePoint, false, new FlagSet()));
  }
}
