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

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.sonarsource.analyzer.commons.regex.RegexIssueLocation;
import org.sonarsource.analyzer.commons.regex.RegexParser;
import org.sonarsource.analyzer.commons.regex.ast.FlagSet;
import org.sonarsource.analyzer.commons.regex.ast.IndexRange;
import org.sonarsource.analyzer.commons.regex.php.PhpRegexSource;

import static org.assertj.core.api.Assertions.assertThat;
import org.sonarsource.analyzer.commons.regex.RegexIssueReporter;
import org.sonarsource.analyzer.commons.regex.RegexParseResult;

class RedundantRegexAlternativesFinderTest {

  @Test
  void test() {
    Verifier.verify(new RedundantRegexAlternativesFinderCheck(), "RedundantRegexAlternativesFinder.yml");
  }

  @Test
  void posix_issue_locations_and_grouping() {
    RegexParseResult result = new RegexParser(new PhpRegexSource("[[:space:]]|\\n|\\r", '\''), new FlagSet()).parse();
    assertThat(result.getSyntaxErrors()).isEmpty();
    AtomicInteger issues = new AtomicInteger();
    new RedundantRegexAlternativesFinder((element, message, cost, secondaries) -> {
      issues.incrementAndGet();
      assertThat(message).isEqualTo("Remove or rework this redundant alternative.");
      assertThat(element.getRange()).isEqualTo(new IndexRange(12, 14));
      assertThat(cost).isNull();
      assertThat(secondaries).extracting(RegexIssueLocation::message)
        .containsExactly("Alternative to keep", "Other redundant alternative");
      assertThat(secondaries.get(0).syntaxElements().get(0).getRange()).isEqualTo(new IndexRange(0, 11));
      assertThat(secondaries.get(1).syntaxElements().get(0).getRange()).isEqualTo(new IndexRange(15, 17));
    }).visit(result);
    assertThat(issues.get()).isEqualTo(1);
  }

  static class RedundantRegexAlternativesFinderCheck extends FinderCheck {
    @Override
    public void checkRegex(RegexParseResult parseResult, RegexIssueReporter.ElementIssue regexElementIssueReporter, RegexIssueReporter.InvocationIssue invocationIssueReporter) {
      new RedundantRegexAlternativesFinder(regexElementIssueReporter).visit(parseResult);
    }
  }

}
