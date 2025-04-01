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

class FailingLookaheadFinderTest {

  // The behavior should be the same for Partial, Both, Unknown and Not Supported.
  @Test
  void test() {
    Verifier.verify(new FailingLookaheadFinderCheck(), "FailingLookaheadFinder.yml");
  }

  @Test
  void test_partial_match() {
    Verifier.verify(new FailingLookaheadFinderCheckWithMatchType(MatchType.PARTIAL), "FailingLookaheadFinder.yml");
  }

  @Test
  void test_unknown_match() {
    Verifier.verify(new FailingLookaheadFinderCheckWithMatchType(MatchType.UNKNOWN), "FailingLookaheadFinder.yml");
  }

  @Test
  void test_both_match() {
    Verifier.verify(new FailingLookaheadFinderCheckWithMatchType(MatchType.BOTH), "FailingLookaheadFinder.yml");
  }

  @Test
  void test_full_match() {
    Verifier.verify(new FailingLookaheadFinderCheckWithMatchType(MatchType.FULL), "FailingLookaheadFinderFullMatch.yml");
  }

  static class FailingLookaheadFinderCheck extends FinderCheck {
    @Override
    public void checkRegex(RegexParseResult parseResult, RegexIssueReporter.ElementIssue regexElementIssueReporter, RegexIssueReporter.InvocationIssue invocationIssueReporter) {
      new FailingLookaheadFinder(regexElementIssueReporter, parseResult.getFinalState()).visit(parseResult);
    }
  }

  static class FailingLookaheadFinderCheckWithMatchType extends FinderCheck {
    private final MatchType matchType;

    FailingLookaheadFinderCheckWithMatchType(MatchType matchType) {
      this.matchType = matchType;
    }

    @Override
    public void checkRegex(RegexParseResult parseResult, RegexIssueReporter.ElementIssue regexElementIssueReporter, RegexIssueReporter.InvocationIssue invocationIssueReporter) {
      new FailingLookaheadFinder(regexElementIssueReporter, parseResult.getFinalState(), matchType).visit(parseResult);
    }
  }

}
