/*
 * SonarSource Analyzers Commons Configurations
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
package org.sonarsource.analyzer.commons.appsec.export;

import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.sonarsource.analyzer.commons.appsec.SecretClassifier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RegexTranslatorTest {
  private static final List<String> SAMPLES = List.of(
    "", "abc", "samplepassword", "EXAMPLE_SECRET", "deadbeef", "qwerty",
    "password1234", "passwd", "undefined", "true", "null", "yourpassword",
    "abbbbc", "111111", "1fj28...askn3i", "TODO: replace me", "FIXME",
    "admin123", "vncpass", "super-secret-p4ssw0rd",
    "hunter2", "letmein", "abc123", "changeme", "changeit", "unknown", "optional",
    "enabled", "disabled", "string", "random", "token",
    "${secret}", "value-${pwd}", "#{{secret}}", "((db-password))",
    "$(echo $PASSWORD)", "`echo $PASSWORD`", "$foo_bar",
    "{secret}", "%{secret}", "{{secret}}",
    "System.getenv(\"secret\")", "process.env.MY_SECRET", "%GITHUB_TOKEN%", "config['secret']", "Read-Host",
    "<password>", "(password)", "[password]", "%(password)s", "@variables('name')",
    "encrypted:YWJjZGVm", "{cipher}1e3faa2cdab2deae117dca102e52922a", "enc[QUJDRA==]", "ENC{abcdef}",
    "%enc{QUJDRA==}", "ENC(abcdef)",
    "arn:aws:secretsmanager:us-east-1:123456789012:secret:db-pass", "op://vault/item/password",
    "VAULT[path/to/secret access_token]",
    "/var/keys/gsa-key.json", "v1.2.3", ">=1.0.0", "~1.4.5-alpha", "4.0.9(@types/node@22.13.4)",
    // realistic values that should NOT be excluded
    "Xk9Lm2Qp7Rs4Tv1Wz0", "9f8e7d6c5b4a392817", "Tr0ub4dor&3xpl0!t", "mytoken123", "this_should_remain_unknown");

  static Stream<Arguments> translationCases() {
    return Stream.of(
      // possessive quantifiers become atomic groups
      Arguments.of("a++", "(?>a+)"),
      Arguments.of("a*+", "(?>a*)"),
      Arguments.of("a?+", "(?>a?)"),
      Arguments.of("[^}]++", "(?>[^}]+)"),
      Arguments.of("\\d{0,5}+", "(?>\\d{0,5})"),
      Arguments.of("\\k<repeated>*+", "(?>\\k<repeated>*)"),
      // nested possessive quantifiers, inner and outer both rewritten
      Arguments.of("(?:/[a-z0-9_.-]++){3,}+", "(?>(?:/(?>[a-z0-9_.-]+)){3,})"),
      // escaped delimiters around a possessive negated class
      Arguments.of("^%?\\{[^}]++\\}$", "^%?\\{(?>[^}]+)\\}$"),
      // greedy / lazy / fixed quantifiers are preserved
      Arguments.of("a+", "a+"),
      Arguments.of("a+?", "a+?"),
      Arguments.of("a{2,3}", "a{2,3}"),
      // named groups and backreferences pass through unchanged (no possessive quantifiers)
      Arguments.of("(?<char>[\\w\\*\\.])\\k<char>{3}", "(?<char>[\\w\\*\\.])\\k<char>{3}"),
      // escaped '+' is a literal, not a possessive marker
      Arguments.of("a\\+b", "a\\+b"),
      // a literal '{' (not a valid quantifier) is left alone
      Arguments.of("a{b}", "a{b}"),
      // the two-digit \xHH hex escape is portable to .NET and passes through unchanged
      Arguments.of("\\x41", "\\x41"),
      // the plain \b word boundary and \B non-boundary are portable and pass through unchanged
      Arguments.of("\\bword\\b", "\\bword\\b"),
      Arguments.of("\\Bfoo", "\\Bfoo"),
      // an escaped backslash followed by 'Q' is a literal, not the start of \Q...\E quoting
      Arguments.of("a\\\\Qb", "a\\\\Qb"));
  }

  @ParameterizedTest
  @MethodSource("translationCases")
  void shouldTranslatePossessiveQuantifiersToAtomicGroups(String input, String expected) {
    assertThat(RegexTranslator.toPortableRegex(input)).isEqualTo(expected);
  }

  static Stream<Arguments> unsupportedConstructs() {
    return Stream.of(
      // \Q...\E literal quoting is unsupported by .NET
      Arguments.of("a\\Qliteral.\\Eb", "\\Q...\\E literal quoting"),
      Arguments.of("\\Efoo", "\\Q...\\E literal quoting"),
      // \x{...} variable-length hex is spelled differently across engines; the parser would otherwise corrupt it
      Arguments.of("\\x{1F600}", "\\x{...} variable-length hex escape"),
      Arguments.of("[\\x{41}]", "\\x{...} variable-length hex escape"),
      // \0nn octal escapes are not portably supported
      Arguments.of("\\012", "\\0nn octal escape"),
      // Java-only linebreak / whitespace / grapheme / anchor escapes with no portable .NET equivalent
      Arguments.of("a\\Rb", "\\R linebreak matcher"),
      Arguments.of("\\h+", "\\h / \\H horizontal-whitespace class"),
      Arguments.of("\\H", "\\h / \\H horizontal-whitespace class"),
      Arguments.of("\\v", "\\v / \\V vertical-whitespace class"),
      Arguments.of("[\\V]", "\\v / \\V vertical-whitespace class"),
      Arguments.of("\\X", "\\X grapheme cluster"),
      Arguments.of("\\Gfoo", "\\G end-of-previous-match anchor"),
      // \b{g} grapheme boundary is Java-only (plain \b stays portable)
      Arguments.of("foo\\b{g}bar", "\\b{g} grapheme boundary"));
  }

  @ParameterizedTest
  @MethodSource("unsupportedConstructs")
  void shouldRejectConstructsThatCannotBeTranslatedPortably(String input, String messageFragment) {
    assertThatThrownBy(() -> RegexTranslator.toPortableRegex(input))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining(messageFragment)
      .hasMessageContaining(input);
  }

  @Test
  void exportedPatternsShouldContainNoPossessiveQuantifiers() {
    for (String regex : sourceRegexes()) {
      String portable = RegexTranslator.toPortableRegex(regex);
      assertThat(hasPossessiveQuantifier(portable))
        .as("possessive quantifier remains in translated pattern: %s (from %s)", portable, regex)
        .isFalse();
    }
  }

  @Test
  void exportedPatternsShouldStillCompileAsRegex() {
    for (String regex : sourceRegexes()) {
      assertThat(SecretPatternsExporter.compilePortable(regex))
        .as("translated pattern does not compile: %s", regex)
        .isNotNull();
    }
  }

  @Test
  void translationShouldBeIdempotent() {
    for (String regex : sourceRegexes()) {
      String once = RegexTranslator.toPortableRegex(regex);
      assertThat(RegexTranslator.toPortableRegex(once)).isEqualTo(once);
    }
  }


  @Test
  void translatedPatternsShouldMatchTheSameSamplesAsTheSource() {
    for (String regex : sourceRegexes()) {
      Pattern source = Pattern.compile(regex, Pattern.CASE_INSENSITIVE);
      Pattern portable = SecretPatternsExporter.compilePortable(regex);
      for (String sample : SAMPLES) {
        assertThat(portable.matcher(sample).find())
          .as("translation changed matching of \"%s\" for pattern %s", sample, regex)
          .isEqualTo(source.matcher(sample).find());
      }
    }
  }

  /**
   * Independent detector of possessive quantifiers (a quantifier immediately followed by {@code +}), skipping escapes
   * and character-class contents. Deliberately simpler than the exporter's parser so the two do not share bugs.
   */
  private static boolean hasPossessiveQuantifier(String re) {
    int n = re.length();
    boolean inClass = false;
    int i = 0;
    while (i < n) {
      char c = re.charAt(i);
      if (c == '\\') {
        i += 2;
        continue;
      }
      if (inClass) {
        if (c == ']') {
          inClass = false;
        }
        i++;
        continue;
      }
      if (c == '[') {
        inClass = true;
        i++;
        continue;
      }
      if ((c == '*' || c == '+' || c == '?' || c == '}') && i + 1 < n && re.charAt(i + 1) == '+') {
        return true;
      }
      i++;
    }
    return false;
  }

  private static List<String> sourceRegexes() {
    return SecretClassifier.exportPatternGroups().stream()
      .flatMap(group -> group.regexes().stream())
      .toList();
  }
}
