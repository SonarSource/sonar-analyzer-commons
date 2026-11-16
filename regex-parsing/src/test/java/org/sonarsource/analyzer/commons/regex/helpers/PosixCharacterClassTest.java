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
import org.sonarsource.analyzer.commons.regex.ast.DisjunctionTree;
import org.sonarsource.analyzer.commons.regex.ast.FlagSet;
import org.sonarsource.analyzer.commons.regex.ast.RegexSyntaxElement;
import org.sonarsource.analyzer.commons.regex.java.JavaRegexSource;

import static org.assertj.core.api.Assertions.assertThat;

class PosixCharacterClassTest {

  @Test
  void separates_cached_proofs_for_ascii_and_mixed_unicode_comparisons() {
    var property = new RegexParser(new JavaRegexSource("\\\\p{Alpha}"), new FlagSet()).parse();
    var alternatives = new RegexParser(new JavaRegexSource("a|(?iu:k)"), new FlagSet()).parse();
    assertThat(property.getSyntaxErrors()).isEmpty();
    assertThat(alternatives.getSyntaxErrors()).isEmpty();
    assertThat(Pattern.matches("a|(?iu:k)", "K")).isTrue();
    assertThat(Pattern.matches("\\p{Alpha}", "K")).isFalse();
    var wholeProperty = new SubAutomaton(property.getResult(), property.getFinalState(), false);
    var wholeAlternatives = new SubAutomaton(alternatives.getResult(), alternatives.getFinalState(), false);
    var plainAlternative = ((DisjunctionTree) alternatives.getResult()).getAlternatives().get(0);
    var plain = new SubAutomaton(plainAlternative, alternatives.getFinalState(), false);
    var checker = new SupersetAutomataChecker(false);
    assertThat(checker.check(wholeProperty, wholeAlternatives)).isFalse();
    assertThat(checker.check(wholeProperty, plain)).isTrue();
    assertThat(checker.check(wholeProperty, wholeAlternatives)).isFalse();
    assertThat(checker.check(wholeProperty, plain)).isTrue();
  }

  @ParameterizedTest
  @ValueSource(strings = {"Lower", "Upper", "ASCII", "Alpha", "Digit", "Alnum", "Punct", "Graph", "Print", "Blank", "Cntrl", "XDigit", "Space"})
  void matches_java_pattern(String property) {
    for (int flags : new int[] {0, Pattern.CASE_INSENSITIVE, Pattern.UNICODE_CASE}) {
      for (String regex : new String[] {
        "\\p{" + property + "}", "\\P{" + property + "}", "[^\\p{" + property + "}]",
        "[a\\P{" + property + "}]", "[a[^\\p{" + property + "}]]"}) {
        assertMatchesPattern(regex, flags);
      }
    }
  }

  private static void assertMatchesPattern(String regex, int flags) {
    var parsed = new RegexParser(new JavaRegexSource(regex.replace("\\", "\\\\")), new FlagSet(flags)).parse();
    assertThat(parsed.getSyntaxErrors()).isEmpty();
    var model = SimplifiedRegexCharacterClass.of(parsed.getResult());
    assertThat(model).isNotNull();
    Pattern pattern = Pattern.compile(regex, flags);
    for (int codePoint = 0; codePoint <= 0x100; codePoint++) {
      assertMembership(regex, flags, codePoint, pattern, model, parsed.getResult());
    }
    for (int codePoint : new int[] {0x17F, 0x212A, 0xD800, 0xFFFF, 0x10000, Character.MAX_CODE_POINT}) {
      assertMembership(regex, flags, codePoint, pattern, model, parsed.getResult());
    }
  }

  private static void assertMembership(String regex, int flags, int codePoint, Pattern pattern,
    SimplifiedRegexCharacterClass model, RegexSyntaxElement element) {
    String character = new String(Character.toChars(codePoint));
    boolean expected = pattern.matcher(character).matches();
    var singleton = new SimplifiedRegexCharacterClass();
    singleton.addRange(codePoint, codePoint, element);
    assertThat(model.supersetOf(singleton, false))
      .as("%s flags=%s codepoint=%s", regex, flags, codePoint).isEqualTo(expected);
    assertThat(model.intersects(singleton, false))
      .as("%s flags=%s codepoint=%s", regex, flags, codePoint).isEqualTo(expected);
  }
}
