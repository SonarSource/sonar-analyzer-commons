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
import java.util.Optional;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.sonarsource.analyzer.commons.regex.MatchType;
import org.sonarsource.analyzer.commons.regex.RegexIssueReporter;
import org.sonarsource.analyzer.commons.regex.RegexParser;
import org.sonarsource.analyzer.commons.regex.ast.FlagSet;
import org.sonarsource.analyzer.commons.regex.ast.RegexSyntaxElement;
import org.sonarsource.analyzer.commons.regex.java.JavaRegexSource;

import static org.assertj.core.api.Assertions.assertThat;

class PosixUnicodeSemanticsTest {

  @ParameterizedTest
  @CsvSource(delimiter = ';', value = {
    "\\p{Alpha}++(?iu:k);aK", "\\p{ASCII}++(?iu:s);aſ", "\\P{Lower}++(?iu:K);0k",
    "\\p{Alpha}*+(?iu:k);K", "[\\p{Alpha}]++(?iu:[k]);aK",
    "(?!\\p{Alpha})(?iu:k);K", "(?!\\p{ASCII})(?iu:s);ſ", "(?!\\P{Lower})(?iu:K);k",
    "(?![\\p{Alpha}])(?iu:[k]);K", "(?!\\p{Digit}[a-z])0(?iu:k);0K"
  })
  void preserves_matching_continuations(String regex, String witness) {
    assertThat(Pattern.matches(regex, witness)).as(regex).isTrue();
    assertThat(impossibleMatches(regex)).as(regex).isEmpty();
  }

  @ParameterizedTest
  @CsvSource(delimiter = ';', value = {
    "\\p{Alpha}++k;k", "(?i)\\p{Lower}++K;K", "(?!\\p{Alpha})k;(?!\\p{Alpha})"
  })
  void retains_supported_ascii_reports(String regex, String location) {
    List<RegexSyntaxElement> issues = impossibleMatches(regex);
    assertThat(issues).as(regex).hasSize(1);
    assertThat(issues.get(0).getText()).isEqualTo(location.replace("\\", "\\\\"));
  }

  @ParameterizedTest
  @CsvSource(delimiter = ';', value = {
    "\\p{Alpha}*?(?iu:k);[^k\\P{Alpha}]*+(?iu:k);aKb",
    "(?i:\\p{Lower})*?A;[^A\\P{Lower}]*+A;BA!",
    "\\w*?\\p{Upper};[\\P{Upper}\\W]*+\\p{Upper};!A",
    "(?U:\\w)*?[\\p{Upper}];[^\\p{Upper}\\W]*+[\\p{Upper}];éA!"
  })
  void preserves_property_split_results(String regex, String unsafeReplacement, String witness) {
    assertThat(Pattern.compile(regex).split(witness, -1))
      .isNotEqualTo(Pattern.compile(unsafeReplacement).split(witness, -1));
    assertThat(reluctantReports(regex)).as(regex).isEmpty();
  }

  @Test
  void retains_existing_reluctant_report() {
    assertThat(reluctantReports("\\w*?k")).hasSize(1);
  }

  @ParameterizedTest
  @CsvSource(delimiter = ';', value = {
    "\\P{ASCII}|K;K", "K|\\P{ASCII};K", "\\P{ASCII}++K;éK", "(?!\\P{ASCII})K;K"
  })
  void preserves_canonical_equivalence(String regex, String witness) {
    assertThat(Pattern.compile(regex, Pattern.CANON_EQ).matcher(witness).matches()).isTrue();
    assertThat(impossibleMatches(regex, Pattern.CANON_EQ)).as(regex).isEmpty();
  }

  @Test
  void preserves_literal_mode() {
    String regex = "\\p{Lower}|a";
    Pattern literal = Pattern.compile(regex, Pattern.LITERAL);
    assertThat(literal.matcher(regex).matches()).isTrue();
    assertThat(literal.matcher("a").matches()).isFalse();
    assertThat(impossibleMatches(regex, Pattern.LITERAL)).isEmpty();
  }

  @Test
  void preserves_redos_report_with_uncertain_unicode_complement() {
    String tail = "(?:\\p{Alpha}|(?iu:[^k]))*";
    assertThat(Pattern.matches(tail, "K")).isFalse();
    assertThat(redosReports("(a+)+" + tail)).containsExactly("QUADRATIC_WHEN_OPTIMIZED");
    String asciiTail = "(?:\\p{Alpha}|\\P{Alpha})*";
    assertThat(Pattern.matches(asciiTail, "K")).isTrue();
    assertThat(redosReports("(a+)+" + asciiTail)).isEmpty();
  }

  private static List<String> redosReports(String regex) {
    var parsed = new RegexParser(new JavaRegexSource(regex.replace("\\", "\\\\")), new FlagSet()).parse();
    assertThat(parsed.getSyntaxErrors()).isEmpty();
    List<String> issues = new ArrayList<>();
    new RedosFinder() {
      @Override
      protected Optional<String> message(BacktrackingType type, boolean hasBackReference) {
        return type == BacktrackingType.NO_ISSUE ? Optional.empty() : Optional.of(type.name());
      }
    }.checkRegex(parsed, MatchType.FULL, (tree, message, cost, secondaries) -> issues.add(message));
    return issues;
  }

  private static List<RegexSyntaxElement> reluctantReports(String regex) {
    var parsed = new RegexParser(new JavaRegexSource(regex.replace("\\", "\\\\")), new FlagSet()).parse();
    assertThat(parsed.getSyntaxErrors()).isEmpty();
    List<RegexSyntaxElement> issues = new ArrayList<>();
    new ReluctantQuantifierFinder((tree, message, cost, secondaries) -> issues.add(tree)).visit(parsed);
    return issues;
  }

  private static List<RegexSyntaxElement> impossibleMatches(String regex) {
    return impossibleMatches(regex, 0);
  }

  private static List<RegexSyntaxElement> impossibleMatches(String regex, int flags) {
    var parsed = new RegexParser(new JavaRegexSource(regex.replace("\\", "\\\\")), new FlagSet(flags)).parse();
    assertThat(parsed.getSyntaxErrors()).isEmpty();
    List<RegexSyntaxElement> issues = new ArrayList<>();
    RegexIssueReporter.ElementIssue reporter = (tree, message, cost, secondaries) -> issues.add(tree);
    new PossessiveQuantifierContinuationFinder(reporter, parsed.getFinalState()).visit(parsed);
    new FailingLookaheadFinder(reporter, parsed.getFinalState()).visit(parsed);
    new RedundantRegexAlternativesFinder(reporter).visit(parsed);
    return issues;
  }
}
