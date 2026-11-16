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
import org.sonarsource.analyzer.commons.regex.RegexParseResult;
import org.sonarsource.analyzer.commons.regex.RegexParser;
import org.sonarsource.analyzer.commons.regex.ast.FlagSet;
import org.sonarsource.analyzer.commons.regex.ast.RegexTree;
import org.sonarsource.analyzer.commons.regex.java.JavaRegexSource;
import org.sonarsource.analyzer.commons.regex.php.PhpRegexSource;

import static org.assertj.core.api.Assertions.assertThat;

class SimplifiedRegexCharacterClassPosixTest {

  @ParameterizedTest
  @ValueSource(strings = {"Lower", "Upper", "ASCII", "Alpha", "Digit", "Alnum", "Punct", "Graph", "Print", "Blank", "Cntrl", "XDigit", "Space"})
  void matches_java_pattern(String property) {
    for (int flags : new int[] {0, Pattern.CASE_INSENSITIVE, Pattern.UNICODE_CASE}) {
      for (String regex : new String[] {"\\p{" + property + "}", "\\P{" + property + "}", "[^\\p{" + property + "}]"}) {
        Pattern pattern = Pattern.compile(regex, flags);
        RegexTree tree = parse(regex, flags);
        SimplifiedRegexCharacterClass characters = SimplifiedRegexCharacterClass.of(tree);
        assertThat(characters).isNotNull();
        for (int codePoint = 0; codePoint <= 255; codePoint++) {
          assertMatches(pattern, characters, tree, codePoint);
        }
        for (int codePoint : new int[] {0x130, 0x131, 0x17F, 0x212A, 0x2028, 0x1F600, Character.MAX_CODE_POINT}) {
          assertMatches(pattern, characters, tree, codePoint);
        }
      }
    }
  }

  @Test
  void unicode_case_folding_does_not_prove_universal_matching() {
    String regex = "[\\p{Lower}[^k]]";
    int flags = Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE;
    assertThat(Pattern.compile(regex, flags).matcher("\u212A").matches()).isFalse();
    SimplifiedRegexCharacterClass characters = SimplifiedRegexCharacterClass.of(parse(regex, flags));
    assertThat(characters).isNotNull();
    assertThat(characters.matchesAnyCharacter()).isFalse();
    SimplifiedRegexCharacterClass ascii = SimplifiedRegexCharacterClass.of(parse("\\p{ASCII}", 0));
    assertThat(ascii).isNotNull();
    assertThat(characters.intersects(ascii, true)).isTrue();
    assertThat(characters.intersects(ascii, false)).isFalse();
    assertThat(characters.findIntersections(ascii)).isEmpty();
    assertThat(characters.supersetOf(ascii, false)).isFalse();
  }

  @Test
  void java_properties_remain_unknown_for_php_sources() {
    RegexParseResult result = new RegexParser(new PhpRegexSource("\\p{Lower}", '\''), new FlagSet()).parse();
    assertThat(result.getSyntaxErrors()).isEmpty();
    SimplifiedRegexCharacterClass characters = SimplifiedRegexCharacterClass.of(result.getResult());
    SimplifiedRegexCharacterClass letter = SimplifiedRegexCharacterClass.of(parse("a", 0));
    assertThat(characters).isNotNull();
    assertThat(letter).isNotNull();
    assertThat(characters.supersetOf(letter, false)).isFalse();
    assertThat(characters.intersects(letter, false)).isFalse();
    assertThat(characters.intersects(letter, true)).isTrue();
  }

  private static void assertMatches(Pattern pattern, SimplifiedRegexCharacterClass characters, RegexTree tree, int codePoint) {
    SimplifiedRegexCharacterClass singleton = new SimplifiedRegexCharacterClass();
    singleton.addRange(codePoint, codePoint, tree);
    boolean expected = pattern.matcher(new String(Character.toChars(codePoint))).matches();
    assertThat(characters.intersects(singleton, false))
      .as("%s flags=%s intersection U+%04X", pattern, pattern.flags(), codePoint).isEqualTo(expected);
    assertThat(characters.supersetOf(singleton, false))
      .as("%s flags=%s superset U+%04X", pattern, pattern.flags(), codePoint).isEqualTo(expected);
  }

  private static RegexTree parse(String regex, int flags) {
    RegexParseResult result = new RegexParser(new JavaRegexSource(regex.replace("\\", "\\\\")), new FlagSet(flags)).parse();
    assertThat(result.getSyntaxErrors()).isEmpty();
    return result.getResult();
  }
}
