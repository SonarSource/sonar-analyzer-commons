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
import org.sonarsource.analyzer.commons.regex.ast.CharacterClassElementTree;
import org.sonarsource.analyzer.commons.regex.ast.FlagSet;
import org.sonarsource.analyzer.commons.regex.ast.IndexRange;
import org.sonarsource.analyzer.commons.regex.ast.PosixCharacterClassElementTree;
import org.sonarsource.analyzer.commons.regex.php.PhpRegexSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class PosixCharacterClassTest {

  private static final CharacterClassElementTree ELEMENT = mock(CharacterClassElementTree.class);

  @ParameterizedTest
  @CsvSource({
    "alnum, [A-Za-z0-9]",
    "alpha, [A-Za-z]",
    "ascii, [\\x00-\\x7f]",
    "blank, [ \\t]",
    "cntrl, [\\x00-\\x1f\\x7f]",
    "digit, [0-9]",
    "graph, [!-~]",
    "lower, [a-z]",
    "print, [ -~]",
    "punct, [!-/:-@\\[-`{-~]",
    "space, [ \\t-\\r]",
    "upper, [A-Z]",
    "word, [A-Za-z0-9_]",
    "xdigit, [A-Fa-f0-9]"
  })
  void membership_and_complements(String property, String expectedRegex) {
    for (int flags : new int[] {0, Pattern.CASE_INSENSITIVE, Pattern.UNICODE_CHARACTER_CLASS,
      Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CHARACTER_CLASS}) {
      Pattern expected = Pattern.compile(expectedRegex, flags & Pattern.CASE_INSENSITIVE);
      for (boolean negateElement : new boolean[] {false, true}) {
        for (boolean negateClass : new boolean[] {false, true}) {
          String regex = "[" + (negateClass ? "^" : "") + "[:" + (negateElement ? "^" : "") + property + ":]]";
          SimplifiedRegexCharacterClass characters = parse(regex, flags);
          boolean unknown = (flags & Pattern.UNICODE_CHARACTER_CLASS) != 0 && !"ascii".equals(property);
          assertThat(characters.isEmpty()).isFalse();
          assertThat(characters.matchesAnyCharacter()).isFalse();
          for (int codePoint = 0; codePoint <= 256; codePoint++) {
            boolean member = expected.matcher(Character.toString(codePoint)).matches() ^ negateElement ^ negateClass;
            assertThat(characters.supersetOf(singleton(codePoint), false))
              .as("%s flags=%s character=%s", regex, flags, codePoint)
              .isEqualTo(member && (!unknown || codePoint < 128));
          }
          assertThat(characters.supersetOf(singleton(Character.MAX_CODE_POINT), false))
            .isEqualTo(!unknown && negateElement != negateClass);
        }
      }
    }
  }

  @Test
  void unions_preserve_known_members_and_unknown_membership() {
    SimplifiedRegexCharacterClass union = parse("[[:alpha:]0-9_]", Pattern.UNICODE_CHARACTER_CLASS);
    assertThat(union.supersetOf(parse("[A-Za-z0-9_]", 0), false)).isTrue();
    assertThat(union.supersetOf(singleton('é'), false)).isFalse();
    assertThat(parse("[^[:alpha:]0-9_]", Pattern.UNICODE_CHARACTER_CLASS).supersetOf(singleton('!'), false)).isTrue();
    assertThat(parse("[^[:alpha:]0-9_]", Pattern.UNICODE_CHARACTER_CLASS).supersetOf(singleton('_'), false)).isFalse();
    assertThat(parse("[[:^digit:]0-9]", 0).matchesAnyCharacter()).isTrue();
    assertThat(parse("[[:digit:][:^digit:]]", Pattern.UNICODE_CHARACTER_CLASS).matchesAnyCharacter()).isFalse();
    assertThat(parse("[[:digit:][:^digit:]]", Pattern.UNICODE_CHARACTER_CLASS).supersetOf(singleton('x'), false)).isTrue();
  }

  @Test
  void unsupported_classes_are_unknown() {
    PhpRegexSource source = new PhpRegexSource("", '\'');
    for (String property : new String[] {"unknown", "<", ">"}) {
      for (boolean negated : new boolean[] {false, true}) {
        SimplifiedRegexCharacterClass unknown = new SimplifiedRegexCharacterClass(
          new PosixCharacterClassElementTree(source, new IndexRange(0, 0), negated, property, new FlagSet()));
        assertThat(unknown.isEmpty()).isFalse();
        assertThat(unknown.matchesAnyCharacter()).isFalse();
        assertThat(unknown.supersetOf(singleton('x'), false)).isFalse();
        assertThat(unknown.supersetOf(singleton(Character.MAX_CODE_POINT), false)).isFalse();
        assertThat(parse("[[:ascii:]]", 0).supersetOf(unknown, false)).isFalse();
        assertThat(unknown.intersects(singleton('x'), true)).isTrue();
      }
    }
  }

  private static SimplifiedRegexCharacterClass singleton(int codePoint) {
    SimplifiedRegexCharacterClass characters = new SimplifiedRegexCharacterClass();
    characters.addRange(codePoint, codePoint, ELEMENT);
    return characters;
  }

  private static SimplifiedRegexCharacterClass parse(String regex, int flags) {
    var result = new RegexParser(new PhpRegexSource(regex, '\''), new FlagSet(flags)).parse();
    assertThat(result.getSyntaxErrors()).isEmpty();
    return SimplifiedRegexCharacterClass.of(result.getResult());
  }
}
