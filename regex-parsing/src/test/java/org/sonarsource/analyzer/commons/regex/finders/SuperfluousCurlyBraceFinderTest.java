package org.sonarsource.analyzer.commons.regex.finders;

import org.junit.jupiter.api.Test;
import org.sonarsource.analyzer.commons.regex.RegexIssueReporter;
import org.sonarsource.analyzer.commons.regex.RegexParseResult;

public class SuperfluousCurlyBraceFinderTest {

  @Test
  void test() {
    Verifier.verify(new SuperfluousCurlyBraceFinderTest.SuperfluousCurlyBraceFinderCheck(), "SuperfluousCurlyBraceFinder.yml");
  }

  static class SuperfluousCurlyBraceFinderCheck extends FinderCheck {
    @Override
    public void checkRegex(RegexParseResult parseResult, RegexIssueReporter.ElementIssue regexElementIssueReporter, RegexIssueReporter.InvocationIssue invocationIssueReporter) {
      new SuperfluousCurlyBraceFinder(regexElementIssueReporter).visit(parseResult);
    }
  }

}