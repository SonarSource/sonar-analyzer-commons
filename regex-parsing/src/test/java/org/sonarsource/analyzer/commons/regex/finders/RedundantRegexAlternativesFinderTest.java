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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.sonarsource.analyzer.commons.regex.RegexIssueLocation;
import org.sonarsource.analyzer.commons.regex.RegexIssueReporter;
import org.sonarsource.analyzer.commons.regex.RegexParseResult;
import org.sonarsource.analyzer.commons.regex.RegexParser;
import org.sonarsource.analyzer.commons.regex.ast.FlagSet;
import org.sonarsource.analyzer.commons.regex.java.JavaRegexSource;

import static org.assertj.core.api.Assertions.assertThat;

class RedundantRegexAlternativesFinderTest {

  @Test
  void test() {
    Verifier.verify(new RedundantRegexAlternativesFinderCheck(), "RedundantRegexAlternativesFinder.yml");
  }

  @ParameterizedTest
  @CsvSource(delimiter = ';', value = {
    "\\p{Lower}|a; a; \\p{Lower}",
    "a|\\p{Lower}; a; \\p{Lower}",
    "\\p{Upper}|Z; Z; \\p{Upper}",
    "\\p{ASCII}|A; A; \\p{ASCII}",
    "\\p{Alpha}|[a-z]; [a-z]; \\p{Alpha}",
    "\\p{Digit}|[0-9]; [0-9]; \\p{Digit}",
    "\\p{Alnum}|[A-Z0-9]; [A-Z0-9]; \\p{Alnum}",
    "\\p{Punct}|[!?]; [!?]; \\p{Punct}",
    "\\p{Graph}|[a-z]; [a-z]; \\p{Graph}",
    "\\p{Print}|\\x20; \\x20; \\p{Print}",
    "\\p{Blank}|\\t; \\t; \\p{Blank}",
    "\\p{Cntrl}|\\x7F; \\x7F; \\p{Cntrl}",
    "\\p{XDigit}|[0-9a-f]; [0-9a-f]; \\p{XDigit}",
    "\\p{Space}|\\n; \\n; \\p{Space}",
    "\\P{Digit}|a; a; \\P{Digit}",
    "[^\\p{Digit}]|a; a; [^\\p{Digit}]",
    "(?i:\\p{Lower})|A; A; (?i:\\p{Lower})",
    "(?i:\\P{Lower})|!; !; (?i:\\P{Lower})",
    "(?U:\\p{ASCII})|A; A; (?U:\\p{ASCII})",
    "\\p{Alpha}+|abc; abc; \\p{Alpha}+",
    "\\p{Lower}|(a); (a); \\p{Lower}"
  })
  void java_posix_redundant_alternatives(String regex, String redundant, String alternativeToKeep) {
    List<String> issues = new ArrayList<>();
    List<RegexIssueLocation> secondaries = new ArrayList<>();
    JavaRegexSource source = new JavaRegexSource(regex.replace("\\", "\\\\"));
    RegexParseResult result = new RegexParser(source, new FlagSet()).parse();
    assertThat(result.getSyntaxErrors()).isEmpty();
    new RedundantRegexAlternativesFinder((tree, message, cost, locations) -> {
      assertThat(message).isEqualTo(RedundantRegexAlternativesFinder.MESSAGE);
      issues.add(source.substringAt(tree.getRange()));
      secondaries.addAll(locations);
    }).visit(result);
    assertThat(issues).containsExactly(redundant.replace("\\", "\\\\"));
    assertThat(secondaries).hasSize(1);
    assertThat(secondaries.get(0).message()).isEqualTo(RedundantRegexAlternativesFinder.MESSAGE_KEEP);
    assertThat(source.substringAt(secondaries.get(0).syntaxElements().get(0).getRange())).isEqualTo(alternativeToKeep.replace("\\", "\\\\"));
  }

  @ParameterizedTest
  @ValueSource(strings = {
    "\\p{Lower}|A", "\\p{Digit}|a", "\\p{ASCII}|é", "\\P{Digit}|0",
    "(?i:\\P{Lower})|A", "(?U:\\p{Punct})|$", "(?U:\\p{Lower})|A",
    "[a-z]|(?U:\\p{Lower})", "[^\\p{Digit}]|1",
    "(a)|\\p{Lower}"
  })
  void java_posix_nonredundant_or_unknown_alternatives(String regex) {
    RegexParseResult result = new RegexParser(new JavaRegexSource(regex.replace("\\", "\\\\")), new FlagSet()).parse();
    assertThat(result.getSyntaxErrors()).isEmpty();
    List<RegexIssueLocation> issues = new ArrayList<>();
    new RedundantRegexAlternativesFinder((tree, message, cost, secondaries) -> issues.add(new RegexIssueLocation(tree, message))).visit(result);
    assertThat(issues).isEmpty();
  }

  static class RedundantRegexAlternativesFinderCheck extends FinderCheck {
    @Override
    public void checkRegex(RegexParseResult parseResult, RegexIssueReporter.ElementIssue regexElementIssueReporter, RegexIssueReporter.InvocationIssue invocationIssueReporter) {
      new RedundantRegexAlternativesFinder(regexElementIssueReporter).visit(parseResult);
    }
  }

}
