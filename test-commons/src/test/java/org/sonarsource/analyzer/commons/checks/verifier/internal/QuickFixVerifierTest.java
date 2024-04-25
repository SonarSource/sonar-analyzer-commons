package org.sonarsource.analyzer.commons.checks.verifier.internal;

import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.Test;
import org.sonarsource.analyzer.commons.checks.verifier.CommentParser;
import org.sonarsource.analyzer.commons.checks.verifier.SingleFileVerifier;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.Assert.*;

public class QuickFixVerifierTest {

  @Test
  public void test_assert_no_issues_raised() {
    Path path = Paths.get("src/test/resources/JavaCodeWithQuickFix.java");
    var verifier = SingleFileVerifier.create(path, UTF_8).withQuickFixes();
    CommentParser.create()
      .addSingleLineCommentSyntax("//")
      .parseInto(path, verifier);
    verifier.reportIssue("issue").onLine(3);
    verifier.reportIssue("issue").onLine(9);
    verifier.reportIssue("issue").onLine(14);
    verifier.reportIssue("issue").onLine(22);
    verifier.reportIssue("issue").onLine(30);
    verifier.assertOneOrMoreIssues();
  }

}