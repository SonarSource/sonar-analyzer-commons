package org.sonarsource.analyzer.commons.regex;

import java.util.List;
import javax.annotation.Nullable;
import org.sonarsource.analyzer.commons.regex.ast.RegexSyntaxElement;

public final class RegexIssueReporter {

  @FunctionalInterface
  public interface ElementIssue {
    void report(RegexSyntaxElement syntaxElement, String message, @Nullable Integer cost, List<RegexIssueLocation> secondaries);
  }

  @FunctionalInterface
  public interface InvocationIssue {
    void report(String message, @Nullable Integer cost, List<RegexIssueLocation> secondaries);
  }
}
