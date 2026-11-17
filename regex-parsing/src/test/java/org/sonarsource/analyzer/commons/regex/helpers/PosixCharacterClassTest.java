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
import org.sonarsource.analyzer.commons.regex.RegexParseResult;
import org.sonarsource.analyzer.commons.regex.RegexParser;
import org.sonarsource.analyzer.commons.regex.ast.FlagSet;
import org.sonarsource.analyzer.commons.regex.ast.IndexRange;
import org.sonarsource.analyzer.commons.regex.ast.PosixCharacterClassElementTree;
import org.sonarsource.analyzer.commons.regex.php.PhpRegexSource;

import static org.assertj.core.api.Assertions.assertThat;

class PosixCharacterClassTest {

  @ParameterizedTest
  @CsvSource({
    "alnum, [a-zA-Z0-9]", "alpha, [a-zA-Z]", "ascii, [\\x00-\\x7f]",
    "blank, [ \\t]", "cntrl, [\\x00-\\x1f\\x7f]", "digit, [0-9]",
    "graph, [!-~]", "print, [ -~]", "lower, [a-z]", "upper, [A-Z]",
    "punct, [!-/:-@\\[-`{-~]", "space, [ \\t-\\r]", "word, [a-zA-Z0-9_]", "xdigit, [0-9a-fA-F]"
  })
  void ascii_membership_and_complement(String property, String expected) {
    // Compare complete sets in both directions, including NUL, DEL and all non-ASCII values.
    for (int flags : new int[] {0, Pattern.CASE_INSENSITIVE}) {
      assertEquivalent("[[:" + property + ":]]", expected, flags);
      String complement = "[^" + expected.substring(1);
      assertEquivalent("[[:^" + property + ":]]", complement, flags);
      assertEquivalent("[^[:" + property + ":]]", complement, flags);
      assertEquivalent("[^[:^" + property + ":]]", expected, flags);
    }
  }

  @Test
  void unions_ranges_and_case_flags() {
    assertEquivalent("[[:digit:]a-f_]", "[0-9a-f_]", 0);
    assertEquivalent("[[:digit:][:alpha:]]", "[a-zA-Z0-9]", 0);
    assertEquivalent("[[:^digit:]0-9]", "[\\s\\S]", 0);
    assertEquivalent("[^[:^digit:]5]", "[0-46-9]", 0);
    assertEquivalent("[[:lower:]]", "[a-zA-Z]", Pattern.CASE_INSENSITIVE);
    assertEquivalent("[[:^lower:]]", "[^a-zA-Z]", Pattern.CASE_INSENSITIVE);
    assertEquivalent("[[:upper:]]", "[A-Z]", 0);
  }

  @ParameterizedTest
  @CsvSource({"alnum, A, !", "alpha, Z, 1", "blank, ' ', A", "cntrl, \\x7f, A",
    "digit, 5, A", "graph, !, ' '", "print, ' ', \\n", "lower, a, A", "upper, A, a",
    "punct, _, A", "space, \\n, x", "word, _, !", "xdigit, F, G"})
  void unicode_mode_preserves_ascii_but_does_not_guess_additional_membership(String property, String member, String nonMember) {
    int flags = Pattern.UNICODE_CHARACTER_CLASS;
    String positive = "[[:" + property + ":]]";
    String negative = "[[:^" + property + ":]]";
    assertThat(parse(positive, flags).supersetOf(parse(member, flags), false)).isTrue();
    assertThat(parse(positive, flags).supersetOf(parse(nonMember, flags), false)).isFalse();
    assertThat(parse(negative, flags).supersetOf(parse(member, flags), false)).isFalse();
    assertThat(parse(negative, flags).supersetOf(parse(nonMember, flags), false)).isTrue();
    for (String regex : new String[] {positive, negative, "[^[:" + property + ":]]", "[^[:^" + property + ":]]"}) {
      SimplifiedRegexCharacterClass characters = parse(regex, flags);
      assertThat(characters.isEmpty()).as(regex).isFalse();
      assertThat(characters.matchesAnyCharacter()).as(regex).isFalse();
      assertThat(characters.supersetOf(parse("é", flags), false)).as(regex).isFalse();
      assertThat(parse("[\\s\\S]", flags).supersetOf(characters, false)).as(regex).isFalse();
    }
    assertThat(parse("[^[:^" + property + ":]]", flags).supersetOf(parse(member, flags), false)).isTrue();
    assertThat(parse("[^[:" + property + ":]]", flags).supersetOf(parse(nonMember, flags), false)).isTrue();
  }

  @Test
  void ascii_remains_exact_in_unicode_mode() {
    assertEquivalent("[[:ascii:]]", "[\\x00-\\x7f]", Pattern.UNICODE_CHARACTER_CLASS);
    assertEquivalent("[[:^ascii:]]", "[^\\x00-\\x7f]", Pattern.UNICODE_CHARACTER_CLASS);
    assertEquivalent("[^[:^ascii:]]", "[\\x00-\\x7f]", Pattern.UNICODE_CHARACTER_CLASS);
  }

  @Test
  void unknown_properties_and_word_boundaries_are_neither_empty_nor_universal() {
    for (String property : new String[] {"unknown", "<", ">"}) {
      for (boolean negated : new boolean[] {false, true}) {
        PosixCharacterClassElementTree tree = new PosixCharacterClassElementTree(
          new PhpRegexSource("", '\''), new IndexRange(0, 0), negated, property, new FlagSet());
        SimplifiedRegexCharacterClass characters = new SimplifiedRegexCharacterClass(tree);
        assertThat(characters.isEmpty()).isFalse();
        assertThat(characters.matchesAnyCharacter()).isFalse();
        assertThat(characters.supersetOf(parse("x", 0), false)).isFalse();
        assertThat(characters.supersetOf(parse("\\x{10ffff}", 0), false)).isFalse();
        assertThat(parse("x", 0).supersetOf(characters, false)).isFalse();
      }
    }
  }

  private static void assertEquivalent(String actual, String expected, int flags) {
    SimplifiedRegexCharacterClass actualSet = parse(actual, flags);
    SimplifiedRegexCharacterClass expectedSet = parse(expected, flags);
    assertThat(actualSet.supersetOf(expectedSet, false)).as("%s covers %s (flags %s)", actual, expected, flags).isTrue();
    assertThat(expectedSet.supersetOf(actualSet, false)).as("%s covers %s (flags %s)", expected, actual, flags).isTrue();
  }

  private static SimplifiedRegexCharacterClass parse(String regex, int flags) {
    RegexParseResult result = new RegexParser(new PhpRegexSource(regex, '\''), new FlagSet(flags)).parse();
    assertThat(result.getSyntaxErrors()).isEmpty();
    SimplifiedRegexCharacterClass characters = SimplifiedRegexCharacterClass.of(result.getResult());
    assertThat(characters).isNotNull();
    return characters;
  }
}
