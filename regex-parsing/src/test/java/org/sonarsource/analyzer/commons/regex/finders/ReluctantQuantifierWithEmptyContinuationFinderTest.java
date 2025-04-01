/*
 * SonarSource Analyzers Regex Parsing Commons
 * Copyright (C) 2009-2025 SonarSource SA
 * mailto:info AT sonarsource DOT com
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the Sonar Source-Available License Version 1, as published by SonarSource SA.
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

import org.junit.jupiter.api.Test;
import org.sonarsource.analyzer.commons.regex.MatchType;
import org.sonarsource.analyzer.commons.regex.RegexIssueReporter;
import org.sonarsource.analyzer.commons.regex.RegexParseResult;

class ReluctantQuantifierWithEmptyContinuationFinderTest {

  @Test
  void test() {
    Verifier.verify(new ReluctantQuantifierWithEmptyContinuationFinderCheck(), "ReluctantQuantifierWithEmptyContinuationFinder.yml");
  }

  @Test
  void test_partial_match() {
    // When the MatchType is explicitly PARTIAL, the behavior is the same as when the Type is not set at all.
    Verifier.verify(new ReluctantQuantifierWithEmptyContinuationFinderExplicitMatchTypeCheck(MatchType.PARTIAL), "ReluctantQuantifierWithEmptyContinuationFinder.yml");
  }

  @Test
  void test_full_match() {
    Verifier.verify(new ReluctantQuantifierWithEmptyContinuationFinderExplicitMatchTypeCheck(MatchType.FULL), "ReluctantQuantifierWithEmptyContinuationFinderFullMatch.yml");
  }

  @Test
  void test_both_match() {
    Verifier.verify(new ReluctantQuantifierWithEmptyContinuationFinderExplicitMatchTypeCheck(MatchType.BOTH), "ReluctantQuantifierWithEmptyContinuationFinderBothOrUnknownMatch.yml");
  }

  @Test
  void test_unknown_match() {
    Verifier.verify(new ReluctantQuantifierWithEmptyContinuationFinderExplicitMatchTypeCheck(MatchType.UNKNOWN), "ReluctantQuantifierWithEmptyContinuationFinderBothOrUnknownMatch.yml");
  }

  static class ReluctantQuantifierWithEmptyContinuationFinderCheck extends FinderCheck {
    @Override
    public void checkRegex(RegexParseResult parseResult, RegexIssueReporter.ElementIssue regexElementIssueReporter, RegexIssueReporter.InvocationIssue invocationIssueReporter) {
      new ReluctantQuantifierWithEmptyContinuationFinder(regexElementIssueReporter).visit(parseResult);
    }
  }

  static class ReluctantQuantifierWithEmptyContinuationFinderExplicitMatchTypeCheck extends FinderCheck {
    private final MatchType matchType;

    ReluctantQuantifierWithEmptyContinuationFinderExplicitMatchTypeCheck(MatchType matchType) {
      this.matchType = matchType;
    }

    @Override
    public void checkRegex(RegexParseResult parseResult, RegexIssueReporter.ElementIssue regexElementIssueReporter, RegexIssueReporter.InvocationIssue invocationIssueReporter) {
      new ReluctantQuantifierWithEmptyContinuationFinder(regexElementIssueReporter, matchType).visit(parseResult);
    }
  }

}
