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
  void posix_alternatives_preserve_issue_locations_messages_and_grouping() {
    String regex = "[[:space:]]|\\n|\\r|\\t";
    RegexParseResult result = new RegexParser(new PhpRegexSource(regex, '\''), new FlagSet()).parse();
    assertThat(result.getSyntaxErrors()).isEmpty();
    List<RegexSyntaxElement> primaries = new ArrayList<>();
    new RedundantRegexAlternativesFinder((element, message, cost, secondaries) -> {
      primaries.add(element);
      assertThat(element.getRange().getBeginningOffset()).isEqualTo(regex.indexOf("\\n"));
      assertThat(message).isEqualTo("Remove or rework this redundant alternative.");
      assertThat(cost).isNull();
      assertThat(secondaries).hasSize(3);
      assertThat(secondaries.get(0).message()).isEqualTo("Alternative to keep");
      assertThat(secondaries.get(0).syntaxElements().get(0).getRange().getBeginningOffset()).isZero();
      for (int i = 1; i < secondaries.size(); i++) {
        assertThat(secondaries.get(i).message()).isEqualTo("Other redundant alternative");
        assertThat(secondaries.get(i).syntaxElements().get(0).getRange().getBeginningOffset()).isEqualTo(regex.indexOf(i == 1 ? "\\r" : "\\t"));
      }
    }).visit(result);
    assertThat(primaries).hasSize(1);
  }

  static class RedundantRegexAlternativesFinderCheck extends FinderCheck {
    @Override
    public void checkRegex(RegexParseResult parseResult, RegexIssueReporter.ElementIssue regexElementIssueReporter, RegexIssueReporter.InvocationIssue invocationIssueReporter) {
      new RedundantRegexAlternativesFinder(regexElementIssueReporter).visit(parseResult);
    }
  }

}
