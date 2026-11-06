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
import org.sonarsource.analyzer.commons.regex.RegexIssueReporter;
import org.sonarsource.analyzer.commons.regex.RegexParseResult;
import org.sonarsource.analyzer.commons.regex.RegexParser;
import org.sonarsource.analyzer.commons.regex.ast.FlagSet;
import org.sonarsource.analyzer.commons.regex.ast.RegexSyntaxElement;
import org.sonarsource.analyzer.commons.regex.php.PhpRegexSource;

import static org.assertj.core.api.Assertions.assertThat;

class RedundantRegexAlternativesFinderTest {

  @Test
  void test() {
    Verifier.verify(new RedundantRegexAlternativesFinderCheck(), "RedundantRegexAlternativesFinder.yml");
  }

  @Test
  void posix_issue_locations_and_grouping() {
    RegexParseResult result = new RegexParser(new PhpRegexSource("[[:alpha:]]|A|B", '\''), new FlagSet()).parse();
    assertThat(result.getSyntaxErrors()).isEmpty();
    List<RegexSyntaxElement> reported = new ArrayList<>();
    new RedundantRegexAlternativesFinder((element, message, cost, secondaries) -> {
      reported.add(element);
      assertThat(message).isEqualTo("Remove or rework this redundant alternative.");
      assertThat(element.getRange().getBeginningOffset()).isEqualTo(12);
      assertThat(element.getRange().getEndingOffset()).isEqualTo(13);
      assertThat(secondaries).hasSize(2);
      assertThat(secondaries.get(0).message()).isEqualTo("Alternative to keep");
      assertThat(secondaries.get(0).syntaxElements().get(0).getRange().getBeginningOffset()).isZero();
      assertThat(secondaries.get(0).syntaxElements().get(0).getRange().getEndingOffset()).isEqualTo(11);
      assertThat(secondaries.get(1).message()).isEqualTo("Other redundant alternative");
      assertThat(secondaries.get(1).syntaxElements().get(0).getRange().getBeginningOffset()).isEqualTo(14);
      assertThat(secondaries.get(1).syntaxElements().get(0).getRange().getEndingOffset()).isEqualTo(15);
    }).visit(result);
    assertThat(reported).hasSize(1);
  }

  static class RedundantRegexAlternativesFinderCheck extends FinderCheck {
    @Override
    public void checkRegex(RegexParseResult parseResult, RegexIssueReporter.ElementIssue regexElementIssueReporter, RegexIssueReporter.InvocationIssue invocationIssueReporter) {
      new RedundantRegexAlternativesFinder(regexElementIssueReporter).visit(parseResult);
    }
  }

}
