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
  @CsvSource(delimiter = ';', value = {
    "alnum;[0-9A-Za-z]",
    "alpha;[A-Za-z]",
    "ascii;[\\x00-\\x7f]",
    "blank;[ \\t]",
    "cntrl;[\\x00-\\x1f\\x7f]",
    "digit;[0-9]",
    "graph;[!-~]",
    "lower;[a-z]",
    "print;[ -~]",
    "punct;[!-/:-@\\[-`{-~]",
    "space;[ \\t-\\r]",
    "upper;[A-Z]",
    "word;[0-9A-Z_a-z]",
    "xdigit;[0-9A-Fa-f]"
  })
  void membership_and_complements(String property, String asciiPattern) {
    for (int flags : new int[] {0, Pattern.CASE_INSENSITIVE, Pattern.UNICODE_CHARACTER_CLASS,
      Pattern.UNICODE_CHARACTER_CLASS | Pattern.CASE_INSENSITIVE}) {
      Pattern expectedSet = Pattern.compile(asciiPattern, flags & Pattern.CASE_INSENSITIVE);
      SimplifiedRegexCharacterClass positive = parse("[[:" + property + ":]]", flags);
      SimplifiedRegexCharacterClass negative = parse("[[:^" + property + ":]]", flags);
      SimplifiedRegexCharacterClass outerNegative = parse("[^[:" + property + ":]]", flags);
      SimplifiedRegexCharacterClass doubleNegative = parse("[^[:^" + property + ":]]", flags);
      for (int codePoint : testCodePoints()) {
        boolean expected = expectedSet.matcher(Character.toString(codePoint)).matches();
        boolean known = codePoint < 128 || (flags & Pattern.UNICODE_CHARACTER_CLASS) == 0 || "ascii".equals(property);
        assertMembership(positive, codePoint, expected && known);
        assertMembership(negative, codePoint, !expected && known);
        assertMembership(outerNegative, codePoint, !expected && known);
        assertMembership(doubleNegative, codePoint, expected && known);
      }
    }
  }

  @Test
  void unions_preserve_ranges_and_case_aware_complements() {
    assertMembership(parse("[[:alpha:]0-9_]", 0), '_', true);
    assertMembership(parse("[a-f[:^digit:]]", 0), 'z', true);
    assertMembership(parse("[a-f[:^digit:]]", 0), '5', false);
    assertMembership(parse("[^a-f[:digit:]]", 0), 'z', true);
    assertMembership(parse("[^a-f[:digit:]]", 0), 'a', false);
    assertMembership(parse("[^a-f[:digit:]]", 0), '5', false);
    assertMembership(parse("[^[:lower:]]", Pattern.CASE_INSENSITIVE), 'A', false);
    assertMembership(parse("[^[:lower:]]", 0), 'A', true);
    assertThat(parse("[[:digit:][:^digit:]]", 0).matchesAnyCharacter()).isTrue();
    SimplifiedRegexCharacterClass unicodeUnion = parse("[[:digit:][:^digit:]]", Pattern.UNICODE_CHARACTER_CLASS);
    assertMembership(unicodeUnion, 'a', true);
    assertMembership(unicodeUnion, 0x100, false);
    assertThat(unicodeUnion.matchesAnyCharacter()).isFalse();
    assertThat(unicodeUnion.isEmpty()).isFalse();
  }

  @Test
  void unknown_properties_and_boundary_markers_are_neither_empty_nor_universal() {
    PhpRegexSource source = new PhpRegexSource("", '\'');
    for (String property : new String[] {"unknown", "<", ">"}) {
      for (boolean negated : new boolean[] {false, true}) {
        PosixCharacterClassElementTree tree = new PosixCharacterClassElementTree(source, new IndexRange(0, 0), negated, property, new FlagSet());
        SimplifiedRegexCharacterClass unknown = new SimplifiedRegexCharacterClass(tree);
        assertThat(unknown.isEmpty()).isFalse();
        assertThat(unknown.matchesAnyCharacter()).isFalse();
        assertMembership(unknown, 'a', false);
        assertThat(unknown.supersetOf(parse("[^a]", 0), false)).isFalse();
        assertThat(parse(".", Pattern.DOTALL).supersetOf(unknown, false)).isFalse();
      }
    }
  }

  private static int[] testCodePoints() {
    int[] codePoints = new int[132];
    for (int i = 0; i < 129; i++) {
      codePoints[i] = i;
    }
    codePoints[129] = 0xe9;
    codePoints[130] = 0x100;
    codePoints[131] = Character.MAX_CODE_POINT;
    return codePoints;
  }

  private static void assertMembership(SimplifiedRegexCharacterClass characterClass, int codePoint, boolean expected) {
    SimplifiedRegexCharacterClass singleton = new SimplifiedRegexCharacterClass();
    singleton.addRange(codePoint, codePoint, new PosixCharacterClassElementTree(new PhpRegexSource("", '\''), new IndexRange(0, 0), false, "ascii", new FlagSet()));
    assertThat(characterClass.supersetOf(singleton, false)).as("membership of U+%04X", codePoint).isEqualTo(expected);
  }

  private static SimplifiedRegexCharacterClass parse(String regex, int flags) {
    RegexParseResult result = new RegexParser(new PhpRegexSource(regex, '\''), new FlagSet(flags)).parse();
    assertThat(result.getSyntaxErrors()).isEmpty();
    return SimplifiedRegexCharacterClass.of(result.getResult());
  }
}
