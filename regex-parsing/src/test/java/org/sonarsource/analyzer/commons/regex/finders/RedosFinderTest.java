/*
 * SonarSource Analyzers Regex Parsing Commons
 * Copyright (C) 2009-2023 SonarSource SA
 * mailto:info AT sonarsource DOT com
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package org.sonarsource.analyzer.commons.regex.finders;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.sonarsource.analyzer.commons.regex.MatchType;
import org.sonarsource.analyzer.commons.regex.RegexIssueReporter;
import org.sonarsource.analyzer.commons.regex.RegexParseResult;


class RedosFinderTest {

  @Test
  void test() {
    Verifier.verify(new RedosFinderCheck(MatchType.FULL), "RedosFinderFull.yml");
    Verifier.verify(new RedosFinderCheck(MatchType.PARTIAL), "RedosFinderPartial.yml");
    Verifier.verify(new RedosFinderCheck(MatchType.BOTH), "RedosFinderBoth.yml");
    Verifier.verify(new RedosFinderCheck(MatchType.UNKNOWN), "RedosFinderUnknown.yml");
  }

  static class RedosFinderCheck extends FinderCheck {

    private final MatchType matchType;

    public RedosFinderCheck(MatchType matchType) {
      this.matchType = matchType;
    }

    @Override
    public void checkRegex(RegexParseResult parseResult, RegexIssueReporter.ElementIssue regexElementIssueReporter, RegexIssueReporter.InvocationIssue invocationIssueReporter) {
      new TestRedosFinder().checkRegex(parseResult, matchType, regexElementIssueReporter);
    }
  }

  static class TestRedosFinder extends RedosFinder {
    @Override
    protected Optional<String> message(BacktrackingType foundBacktrackingType, boolean regexContainsBackReference) {
      if (foundBacktrackingType.equals(BacktrackingType.NO_ISSUE)) {
        return Optional.empty();
      }
      return Optional.of(String.format("%s;%s", foundBacktrackingType.name(), regexContainsBackReference));
    }
  }
}
