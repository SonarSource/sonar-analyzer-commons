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
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.sonarsource.analyzer.commons.regex.RegexParseResult;
import org.sonarsource.analyzer.commons.regex.RegexParser;
import org.sonarsource.analyzer.commons.regex.ast.FlagSet;
import org.sonarsource.analyzer.commons.regex.ast.IndexRange;
import org.sonarsource.analyzer.commons.regex.ast.PosixCharacterClassElementTree;
import org.sonarsource.analyzer.commons.regex.ast.RegexTree;
import org.sonarsource.analyzer.commons.regex.java.JavaRegexSource;
import org.sonarsource.analyzer.commons.regex.php.PhpRegexSource;

import static org.assertj.core.api.Assertions.assertThat;

class PosixCharacterClassTest {

  @ParameterizedTest
  @CsvSource({
    "alnum, [a-zA-Z0-9]",
    "alpha, [a-zA-Z]",
    "ascii, [\\x00-\\x7F]",
    "blank, [\\x09\\x20]",
    "cntrl, [\\x00-\\x1F\\x7F]",
    "digit, [0-9]",
    "graph, [\\x21-\\x7E]",
    "lower, [a-z]",
    "print, [\\x20-\\x7E]",
    "punct, [\\x21-\\x2F\\x3A-\\x40\\x5B-\\x60\\x7B-\\x7E]",
    "space, [\\x09-\\x0D\\x20]",
    "upper, [A-Z]",
    "word, [a-zA-Z0-9_]",
    "xdigit, [a-fA-F0-9]"
  })
  void membership_and_complements(String property, String expectedPattern) {
    for (int flags : new int[] {0, Pattern.CASE_INSENSITIVE, Pattern.UNICODE_CHARACTER_CLASS,
      Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CHARACTER_CLASS}) {
      Pattern expected = Pattern.compile(expectedPattern, flags & Pattern.CASE_INSENSITIVE);
      for (boolean negated : new boolean[] {false, true}) {
        String contents = "[:" + (negated ? "^" : "") + property + ":]";
        SimplifiedRegexCharacterClass actual = characterClass("[" + contents + "]", flags);
        SimplifiedRegexCharacterClass complement = characterClass("[^" + contents + "]", flags);
        assertThat(actual.isEmpty()).isFalse();
        assertThat(actual.matchesAnyCharacter()).isFalse();
        for (int codePoint : IntStream.concat(IntStream.rangeClosed(0, 256), IntStream.of(Character.MAX_CODE_POINT)).toArray()) {
          boolean known = codePoint < 128 || (flags & Pattern.UNICODE_CHARACTER_CLASS) == 0 || "ascii".equals(property);
          boolean member = expected.matcher(new String(Character.toChars(codePoint))).matches() != negated;
          SimplifiedRegexCharacterClass singleton = singleton(codePoint);
          assertThat(actual.supersetOf(singleton, false)).as("%s flags=%s codePoint=%s", contents, flags, codePoint)
            .isEqualTo(known && member);
          assertThat(complement.supersetOf(singleton, false)).as("complement of %s flags=%s codePoint=%s", contents, flags, codePoint)
            .isEqualTo(known && !member);
        }
      }
    }
  }

  @Test
  void unicode_unknown_membership_cannot_prove_coverage_or_universality() {
    int flags = Pattern.UNICODE_CHARACTER_CLASS;
    SimplifiedRegexCharacterClass digits = characterClass("[[:digit:]]", flags);
    SimplifiedRegexCharacterClass nonDigits = characterClass("[[:^digit:]]", flags);
    SimplifiedRegexCharacterClass all = characterClass("[\\\\s\\\\S]", 0);
    assertThat(all.supersetOf(digits, false)).isFalse();
    assertThat(digits.supersetOf(digits, false)).isFalse();
    assertThat(nonDigits.supersetOf(nonDigits, false)).isFalse();
    assertThat(characterClass("[[:digit:][:^digit:]]", flags).matchesAnyCharacter()).isFalse();
    assertThat(characterClass("[[:ascii:][:^ascii:]]", flags).matchesAnyCharacter()).isTrue();
    assertThat(characterClass("[^[:digit:]\\\\p{L}]", flags).supersetOf(singleton('!'), false)).isFalse();
    assertThat(characterClass("[^\\\\p{L}[:digit:]]", flags).supersetOf(singleton('!'), false)).isFalse();
    assertThat(characterClass("[x[:digit:]]", flags).supersetOf(singleton('x'), false)).isTrue();
  }

  @Test
  void unmodelled_classes_are_not_empty_or_universal() {
    for (String property : new String[] {"unknown", "<", ">"}) {
      for (boolean negated : new boolean[] {false, true}) {
        SimplifiedRegexCharacterClass unknown = new SimplifiedRegexCharacterClass(new PosixCharacterClassElementTree(
          new JavaRegexSource(""), new IndexRange(0, 0), negated, property, new FlagSet()));
        assertThat(unknown.isEmpty()).isFalse();
        assertThat(unknown.matchesAnyCharacter()).isFalse();
        assertThat(unknown.supersetOf(characterClass("[\\\\s\\\\S]", 0), false)).isFalse();
        assertThat(characterClass("[\\\\s\\\\S]", 0).supersetOf(unknown, false)).isFalse();
      }
    }
  }

  private static SimplifiedRegexCharacterClass characterClass(String regex, int flags) {
    RegexParseResult result = new RegexParser(new PhpRegexSource(regex, '\''), new FlagSet(flags)).parse();
    assertThat(result.getSyntaxErrors()).isEmpty();
    return SimplifiedRegexCharacterClass.of(result.getResult());
  }

  private static SimplifiedRegexCharacterClass singleton(int codePoint) {
    RegexTree tree = SimplifiedRegexCharacterClassTest.parseRegex("x", new FlagSet()).getResult();
    SimplifiedRegexCharacterClass singleton = new SimplifiedRegexCharacterClass();
    singleton.addRange(codePoint, codePoint, tree);
    return singleton;
  }
}
