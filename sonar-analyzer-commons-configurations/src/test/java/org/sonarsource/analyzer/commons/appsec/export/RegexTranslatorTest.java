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
  /**
   * Translation edge cases the published corpus cannot carry: every corpus sample must classify under the category it
   * is declared in, and {@code "FIXME"} is caught by the minimum-length pattern first. It is kept here because it is
   * the only sample that puts a {@code \b} at end of input, a boundary engines can disagree on.
   */
  private static final List<String> TRANSLATION_EDGE_CASES = List.of("FIXME");

  /**
   * Derived from the published validation corpus rather than hand-copied, so a sample added there is exercised here
   * too - this class is the guard that translation preserves matching, and a duplicated list silently drifts.
   */
  private static final List<String> SAMPLES = Stream.of(
    SecretClassifier.exportKnownNonSecretSamples().stream().flatMap(group -> group.values().stream()).toList(),
    SecretClassifier.exportSecretCandidateSamples(),
    TRANSLATION_EDGE_CASES)
    .flatMap(List::stream)
    .toList();

  static Stream<Arguments> translationCases() {
    return Stream.of(
      // possessive quantifiers become plain greedy quantifiers (JS/RE2 support neither possessive nor atomic groups)
      Arguments.of("a++", "a+"),
      Arguments.of("a*+", "a*"),
      Arguments.of("a?+", "a?"),
      Arguments.of("[^}]++", "[^}]+"),
      Arguments.of("\\d{0,5}+", "\\d{0,5}"),
      Arguments.of("\\k<repeated>*+", "\\k<repeated>*"),
      // nested possessive quantifiers, inner and outer both rewritten to greedy
      Arguments.of("(?:/[a-z0-9_.-]++){3,}+", "(?:/[a-z0-9_.-]+){3,}"),
      // escaped delimiters around a possessive negated class
      Arguments.of("^%?\\{[^}]++\\}$", "^%?\\{[^}]+\\}$"),
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
  void shouldRewritePossessiveQuantifiersToGreedy(String input, String expected) {
    assertThat(RegexTranslator.toPortableRegex(input)).isEqualTo(expected);
  }

  static Stream<String> redosProneRewrites() {
    return Stream.of(
      // dropping the possessive marker would leave nested unbounded quantifiers with no atomic-group guard
      "(?:\\w+)++",
      "(a+)*+",
      "(?:[a-z]+)*+",
      "(\\d+){2,}+");
  }

  @ParameterizedTest
  @MethodSource("redosProneRewrites")
  void shouldRejectRewritesThatWouldBecomeReDoSProne(String input) {
    assertThatThrownBy(() -> RegexTranslator.toPortableRegex(input))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("ReDoS-prone")
      .hasMessageContaining(input);
  }

  @Test
  void shouldAllowNestedQuantifiersSeparatedByARequiredLiteral() {
    // Each iteration must start with '/', so this is unambiguous and safe to flatten to greedy - the shape
    // SecretClassifier actually ships. It must not be mistaken for the ReDoS-prone nested form.
    assertThat(RegexTranslator.toPortableRegex("(?:/[^/]++){3,}+")).isEqualTo("(?:/[^/]+){3,}");
  }

  @Test
  void shouldConservativelyRejectUnboundedClassWithTrailingDelimiter() {
    // (?:[^/]++/){2,}+ is in fact safe (each iteration must consume the trailing '/'), but proving that requires
    // showing the leading class excludes the delimiter, and the look-alike (?:.++/){2,}+ is the genuinely
    // ReDoS-prone (.*/)+ form. The first-atom guard deliberately errs toward rejection here rather than build that
    // class-exclusion analysis; see RegexTranslator#rejectNestedUnboundedQuantifier. Pinned so it is not "fixed"
    // into silently accepting the dangerous look-alike.
    assertThatThrownBy(() -> RegexTranslator.toPortableRegex("(?:[^/]++/){2,}+"))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("ReDoS-prone");
  }

  @Test
  void exportedPatternsShouldContainNoAtomicGroups() {
    for (String regex : sourceRegexes()) {
      assertThat(RegexTranslator.toPortableRegex(regex))
        .as("atomic group remains in translated pattern (unsupported by JavaScript): %s", regex)
        .doesNotContain("(?>");
    }
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
