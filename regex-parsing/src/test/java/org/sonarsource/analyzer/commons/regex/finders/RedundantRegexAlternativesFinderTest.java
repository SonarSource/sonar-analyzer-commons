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
package org.sonarsource.analyzer.commons.regex.finders;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.sonarsource.analyzer.commons.regex.RegexIssueReporter;
import org.sonarsource.analyzer.commons.regex.RegexParseResult;
import org.sonarsource.analyzer.commons.regex.RegexParser;
import org.sonarsource.analyzer.commons.regex.RegexSource;
import org.sonarsource.analyzer.commons.regex.ast.FlagSet;
import org.sonarsource.analyzer.commons.regex.ast.RegexSyntaxElement;
import org.sonarsource.analyzer.commons.regex.java.JavaRegexSource;
import org.sonarsource.analyzer.commons.regex.php.PhpRegexSource;

import static org.assertj.core.api.Assertions.assertThat;

class RedundantRegexAlternativesFinderTest {

  @Test
  void test() {
    Verifier.verify(new RedundantRegexAlternativesFinderCheck(), "RedundantRegexAlternativesFinder.yml");
  }

  @ParameterizedTest
  @CsvSource(delimiter = ';', value = {
    "Lower;[a-z]", "Upper;[A-Z]", "ASCII;[\\x00-\\x7F]", "Alpha;[A-Za-z]", "Digit;[0-9]", "Alnum;[a-zA-Z0-9]",
    "Punct;[!-/:-@\\[-`{-~]", "Graph;[!-~]", "Print;[ -~]", "Blank;[ \\t]", "Cntrl;[\\x00-\\x1F\\x7F]",
    "XDigit;[a-fA-F0-9]", "Space;[ \\t-\\r]"
  })
  void equivalent_java_ranges(String property, String range) {
    assertRedundant("\\p{" + property + "}|" + range, range);
    assertRedundant(range + "|\\p{" + property + "}", "\\p{" + property + "}");
    assertRedundant("\\P{" + property + "}|[^" + range + "]", "[^" + range + "]");
  }

  @ParameterizedTest
  @ValueSource(strings = {
    "\\p{Lower}|a", "a|\\p{Lower}", "\\p{Lower}|[abc]", "[\\p{Lower}0-9]|[a-z]", "[A-Z\\p{Lower}]|\\p{Alpha}",
    "\\P{Digit}|a", "[^\\p{Digit}]|a", "[a[^\\p{Lower}]]|a", "[a\\P{Lower}]|a",
    "(?i)\\p{Lower}|A", "(?i)\\p{Upper}|[a-z]", "(?i)\\p{XDigit}|[a-f]", "(?i)\\P{Lower}|0",
    "(?i: \\p{Lower})| A", "(?u)\\p{Lower}|a", "x\\p{Digit}|x[1-3]", "[a-z]x|\\p{Lower}x"
  })
  void redundant_java_alternatives(String regex) {
    assertThat(issues(javaSource(regex), 0)).as(regex).hasSize(1);
  }

  @ParameterizedTest
  @ValueSource(strings = {
    "\\p{Lower}|A", "\\p{Upper}|a", "\\p{ASCII}|é", "\\p{Alpha}|0", "\\p{Alnum}|_", "\\p{Punct}|a",
    "\\p{Graph}| ", "\\p{Print}|\\t", "\\p{Blank}|\\n", "\\p{Cntrl}| ", "\\p{XDigit}|G", "\\p{Space}|\\xA0",
    "\\P{Digit}|5", "[^\\p{Digit}]|5", "(?i)\\P{Lower}|A", "(?i)\\p{Lower}|é",
    "(?U)\\p{Lower}|[a-z]", "(?U)\\P{Lower}|é", "(?iu)\\p{Lower}|[a-z]", "(?iu)[a-z]|\\p{Lower}",
    "(?iu)\\P{Lower}|ſ", "\\p{IsLatin}|a", "\\p{javaLowerCase}|a", "\\p{Unknown}|a", "\\p{lower}|a",
    "\\p{Alpha}|(?iu:k)", "(?iu:k)|\\p{Alpha}", "\\p{ASCII}|(?iu:s)", "(?iu:s)|\\p{ASCII}",
    "\\P{Lower}|(?iu:K)", "(?iu:K)|\\P{Lower}", "[^\\p{Lower}]|(?iu:K)",
    "[\\p{Alpha}0-9]|(?iu:[k])", "\\P{Upper}|(?iu:ſ)", "(?iu:ſ)|\\P{Upper}",
    "\\p{Digit}[a-z]|0(?iu:k)", "0(?iu:k)|\\p{Digit}[a-z]",
    "[a\\p{Unknown}]|b", "[^a\\p{Unknown}]|b", "\\p{Lower}+|[0-9]+", "(a)|\\p{Lower}"
  })
  void preserves_nonredundant_or_unsupported_alternatives(String regex) {
    assertThat(issues(javaSource(regex), 0)).as(regex).isEmpty();
  }

  @Test
  void external_flags_and_dialect_are_respected() {
    assertThat(issues(javaSource("\\p{Lower}|A"), Pattern.CASE_INSENSITIVE)).hasSize(1);
    assertThat(issues(javaSource("[a-z]|\\p{Lower}"), Pattern.UNICODE_CHARACTER_CLASS)).isEmpty();
    assertThat(issues(javaSource("[a-z]|\\p{Lower}"), Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE)).isEmpty();
    assertThat(issues(new PhpRegexSource("\\p{Lower}|a", '\''), 0)).isEmpty();
    assertThat(issues(new PhpRegexSource("[[:lower:]]|a", '\''), 0)).isEmpty();
    assertThat(issues(new PhpRegexSource("a|[[:^lower:]]", '\''), 0)).isEmpty();
  }

  @Test
  void preserves_automaton_repetition_limits() {
    assertThat(issues(javaSource("[0-9]+|[1-3]+"), 0)).isEmpty();
    assertThat(issues(javaSource("\\p{Digit}+|[1-3]+"), 0)).isEmpty();
    assertThat(issues(javaSource("[a-z]{2}|[a-z]{2}"), 0)).isEmpty();
    assertThat(issues(javaSource("[a-z]{2}|\\p{Lower}{2}"), 0)).isEmpty();
  }

  @Test
  void unicode_folded_alternatives_have_additional_matches() {
    assertThat(Pattern.matches("\\p{Alpha}", "K")).isFalse();
    assertThat(Pattern.matches("(?iu:k)", "K")).isTrue();
    assertThat(Pattern.matches("\\p{ASCII}", "ſ")).isFalse();
    assertThat(Pattern.matches("(?iu:s)", "ſ")).isTrue();
    assertThat(Pattern.matches("\\P{Lower}", "k")).isFalse();
    assertThat(Pattern.matches("(?iu:K)", "k")).isTrue();
  }

  private static void assertRedundant(String regex, String redundant) {
    List<RegexSyntaxElement> issues = issues(javaSource(regex), 0);
    assertThat(issues).as(regex).hasSize(1);
    String sourceText = regex.replace("\\", "\\\\");
    String redundantText = redundant.replace("\\", "\\\\");
    assertThat(issues.get(0).getText()).isEqualTo(redundantText);
    int start = sourceText.lastIndexOf(redundantText);
    assertThat(issues.get(0).getRange().getBeginningOffset()).isEqualTo(start);
    assertThat(issues.get(0).getRange().getEndingOffset()).isEqualTo(start + redundantText.length());
  }

  private static JavaRegexSource javaSource(String regex) {
    return new JavaRegexSource(regex.replace("\\", "\\\\"));
  }

  private static List<RegexSyntaxElement> issues(RegexSource source, int flags) {
    RegexParseResult parsed = new RegexParser(source, new FlagSet(flags)).parse();
    assertThat(parsed.getSyntaxErrors()).isEmpty();
    List<RegexSyntaxElement> issues = new ArrayList<>();
    new RedundantRegexAlternativesFinder((tree, message, cost, secondaries) -> {
      assertThat(message).isEqualTo(RedundantRegexAlternativesFinder.MESSAGE);
      assertThat(secondaries).hasSize(1);
      assertThat(secondaries.get(0).message()).isEqualTo(RedundantRegexAlternativesFinder.MESSAGE_KEEP);
      issues.add(tree);
    }).visit(parsed);
    return issues;
  }

  static class RedundantRegexAlternativesFinderCheck extends FinderCheck {
    @Override
    public void checkRegex(RegexParseResult parseResult, RegexIssueReporter.ElementIssue regexElementIssueReporter, RegexIssueReporter.InvocationIssue invocationIssueReporter) {
      new RedundantRegexAlternativesFinder(regexElementIssueReporter).visit(parseResult);
    }
  }

}
