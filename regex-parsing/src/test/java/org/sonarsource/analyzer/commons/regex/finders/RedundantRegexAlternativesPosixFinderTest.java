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
import java.util.stream.Stream;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.sonarsource.analyzer.commons.regex.RegexIssueLocation;
import org.sonarsource.analyzer.commons.regex.RegexParser;
import org.sonarsource.analyzer.commons.regex.RegexParseResult;
import org.sonarsource.analyzer.commons.regex.ast.FlagSet;
import org.sonarsource.analyzer.commons.regex.ast.IndexRange;
import org.sonarsource.analyzer.commons.regex.java.JavaRegexSource;

import static org.assertj.core.api.Assertions.assertThat;

class RedundantRegexAlternativesPosixFinderTest {

  @ParameterizedTest
  @MethodSource("posixExamples")
  void java_posix_alternatives(String property, String included, String excluded) {
    String posix = "\\p{" + property + "}";
    assertRedundant(posix + "|" + included, included, posix);
    assertRedundant(included + "|" + posix, included, posix);
    assertThat(issues(posix + "|" + excluded)).isEmpty();
    assertThat(issues(excluded + "|" + posix)).isEmpty();
  }

  static Stream<Arguments> posixExamples() {
    return Stream.of(
      Arguments.of("Lower", "a", "A"),
      Arguments.of("Upper", "Z", "z"),
      Arguments.of("ASCII", "a", "é"),
      Arguments.of("Alpha", "Z", "0"),
      Arguments.of("Digit", "5", "a"),
      Arguments.of("Alnum", "8", "_"),
      Arguments.of("Punct", "_", "a"),
      Arguments.of("Graph", "!", " "),
      Arguments.of("Print", " ", "\\t"),
      Arguments.of("Blank", "\\t", "\\n"),
      Arguments.of("Cntrl", "\\x{7f}", " "),
      Arguments.of("XDigit", "F", "g"),
      Arguments.of("Space", "\\x{0b}", "\\x{85}"));
  }

  @Test
  void compositions_and_locations() {
    assertRedundant("\\p{Alnum}+|[a-z]", "[a-z]", "\\p{Alnum}+");
    assertRedundant("\\p{Digit}x|[0-9]x", "[0-9]x", "\\p{Digit}x");
    assertRedundant("\\P{Lower}|A", "A", "\\P{Lower}");
    assertRedundant("[^\\p{Digit}]|a", "a", "[^\\p{Digit}]");
    assertRedundant("[\\p{Lower}[^a]]|\\p{ASCII}", "\\p{ASCII}", "[\\p{Lower}[^a]]");
    assertRedundant("[[^a]\\p{Lower}]|\\p{ASCII}", "\\p{ASCII}", "[[^a]\\p{Lower}]");
    assertRedundant("\\p{L}|a|\\p{Lower}", "a", "\\p{Lower}");
    assertRedundant("(?i:\\p{Lower})|A", "A", "(?i:\\p{Lower})");
    assertRedundant("(?i:\\P{Upper})|!", "!", "(?i:\\P{Upper})");
    assertRedundant("(?u:\\p{Lower})|a", "a", "(?u:\\p{Lower})");
    assertRedundant("(?U:\\p{Lower})|x|[xy]", "x", "[xy]");
  }

  @Test
  void preserves_existing_loop_comparison_limit() {
    // The automaton checker cannot prove inclusion when both alternatives contain unbounded loops.
    assertThat(issues("\\p{Alnum}+|[a-z]+")).hasSameSizeAs(issues("[A-Za-z0-9]+|[a-z]+"));
  }

  @ParameterizedTest
  @ValueSource(strings = {
    "\\P{Lower}|a",
    "[^\\p{Digit}]|5",
    "[\\p{Lower}\\p{L}]|A",
    "[a-z]|\\p{L}",
    "\\p{Lower}|é",
    "(?i:\\p{Lower})|é",
    "(?i:\\P{Lower})|A",
    "(?U:\\p{Lower})|[a-z]",
    "[a-z]|(?U:\\p{Lower})",
    "[0-9]|(?U:\\p{Digit})",
    "(?U:\\P{Lower})|é",
    "[^\\p{Lower}]|(?U:\\p{Upper})",
    "\\p{ASCII}|(?iu:k)",
    "(?iu:k)|\\p{ASCII}",
    "(?iu:[^k])|\\P{ASCII}",
    "\\P{ASCII}|(?iu:[^k])",
    "(?iu:\\p{Lower})|[a-z]",
    "\\p{Lower}|[a-z]x",
    "[a-z&&[^b]]|\\p{Lower}",
    "\\p{lower}|a"
  })
  void avoids_false_positives(String regex) {
    assertThat(issues(regex)).isEmpty();
  }

  @Test
  void externally_supplied_unicode_flags() {
    assertThat(issues("[a-z]|\\p{Lower}", Pattern.UNICODE_CHARACTER_CLASS)).isEmpty();
    assertThat(issues("\\p{ASCII}|k", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE)).isEmpty();
    assertThat(issues("[0-9]|\\p{Digit}", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CHARACTER_CLASS)).isEmpty();
  }

  @Test
  void malformed_property_is_ignored() {
    RegexParseResult parsed = new RegexParser(new JavaRegexSource(javaSource("\\p{}|a")), new FlagSet()).parse();
    assertThat(parsed.getSyntaxErrors()).isNotEmpty();
    List<IndexRange> ranges = new ArrayList<>();
    new RedundantRegexAlternativesFinder((tree, message, cost, secondaries) -> ranges.add(tree.getRange())).visit(parsed);
    assertThat(ranges).isEmpty();
  }

  private static void assertRedundant(String regex, String redundant, String keep) {
    List<Issue> issues = issues(regex);
    assertThat(issues).as(regex).hasSize(1);
    String source = javaSource(regex);
    String encodedRedundant = javaSource(redundant);
    String encodedKeep = javaSource(keep);
    Issue issue = issues.get(0);
    int redundantStart = alternativeStart(source, encodedRedundant);
    assertThat(issue.range.getBeginningOffset()).isEqualTo(redundantStart);
    assertThat(issue.range.getEndingOffset()).isEqualTo(redundantStart + encodedRedundant.length());
    assertThat(issue.secondaries).hasSize(1);
    RegexIssueLocation secondary = issue.secondaries.get(0);
    assertThat(secondary.message()).isEqualTo(RedundantRegexAlternativesFinder.MESSAGE_KEEP);
    IndexRange range = secondary.syntaxElements().get(0).getRange();
    int keepStart = alternativeStart(source, encodedKeep);
    assertThat(range.getBeginningOffset()).isEqualTo(keepStart);
    assertThat(range.getEndingOffset()).isEqualTo(keepStart + encodedKeep.length());
  }

  private static int alternativeStart(String source, String alternative) {
    return source.startsWith(alternative + "|") ? 0 : source.indexOf("|" + alternative) + 1;
  }

  private static List<Issue> issues(String regex) {
    return issues(regex, 0);
  }

  private static List<Issue> issues(String regex, int flags) {
    RegexParseResult parsed = new RegexParser(new JavaRegexSource(javaSource(regex)), new FlagSet(flags)).parse();
    assertThat(parsed.getSyntaxErrors()).as(regex).isEmpty();
    List<Issue> issues = new ArrayList<>();
    new RedundantRegexAlternativesFinder((tree, message, cost, secondaries) -> {
      assertThat(message).isEqualTo(RedundantRegexAlternativesFinder.MESSAGE);
      issues.add(new Issue(tree.getRange(), secondaries));
    }).visit(parsed);
    return issues;
  }

  private static String javaSource(String regex) {
    return regex.replace("\\", "\\\\");
  }

  private record Issue(IndexRange range, List<RegexIssueLocation> secondaries) {
  }
}
