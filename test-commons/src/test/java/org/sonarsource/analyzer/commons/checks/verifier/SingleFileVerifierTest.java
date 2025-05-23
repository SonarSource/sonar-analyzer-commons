/*
 * SonarSource Analyzers Test Commons
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
package org.sonarsource.analyzer.commons.checks.verifier;

import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.ComparisonFailure;
import org.junit.Test;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class SingleFileVerifierTest {

  @Test
  public void code_js_simple() throws Exception {
    Path path = Paths.get("src/test/resources/code.js");

    SingleFileVerifier verifier = SingleFileVerifier.create(path, UTF_8);

    verifier.addComment(4, 19, "// Noncompliant", 2, 0);

    verifier.reportIssue("issue").onLine(4);

    verifier.assertOneOrMoreIssues();
  }

  @Test
  public void test_assert_no_issues_raised() {
    Path path = Paths.get("src/test/resources/code.js");
    var verifier = SingleFileVerifier.create(path, UTF_8);
    verifier.assertNoIssuesRaised();

    var verifierWithNonComplComment = SingleFileVerifier.create(path, UTF_8);
    verifierWithNonComplComment.addComment(4, 19, "// Noncompliant", 2, 0);
    verifierWithNonComplComment.assertNoIssuesRaised();

    var verifierWithIssue = SingleFileVerifier.create(path, UTF_8);
    verifierWithIssue.reportIssue("issue").onLine(4);
    assertThatThrownBy(verifierWithIssue::assertNoIssuesRaised)
      .isInstanceOf(AssertionError.class)
      .hasMessage("ERROR: No issues were expected, but some were found. expected:<0> but was:<1>");
  }

  @Test
  public void code_js_with_gap() throws Exception {
    Path path = Paths.get("src/test/resources/code.js");

    SingleFileVerifier verifier = SingleFileVerifier.create(path, UTF_8);

    verifier.addComment(4, 19, "// Noncompliant", 2, 0);

    verifier.reportIssue("issue").onLine(4).withGap(2.5d);

    verifier.assertOneOrMoreIssues();
  }

  @Test
  public void code_js_with_comment_parser() throws Exception {
    Path path = Paths.get("src/test/resources/code.js");

    SingleFileVerifier verifier = SingleFileVerifier.create(path, UTF_8);

    CommentParser.create()
      .addSingleLineCommentSyntax("//")
      .parseInto(path, verifier);

    verifier.reportIssue("Issue on file").onFile();
    verifier.reportIssue("issue1").onLine(4);
    verifier.reportIssue("issue2").onLine(4);

    verifier.reportIssue("msg").onRange(9, 11, 9, 13)
      .addSecondary(6, 9, 6, 11, "msg");

    verifier.reportIssue("Rule message").onRange(12, 5, 12, 9)
      .addSecondary(12, 10, 12, 18, "Secondary location message1")
      .addSecondary(16, 5, 16, 9, "Secondary location message2");

    verifier.reportIssue("Error").onRange(19, 5, 19, 9)
      .withGap(2.5d);

    verifier.reportIssue("msg").onRange(22, 5, 22, 9)
      .addSecondary(22, 12, 22, 16, "msg");

    verifier.reportIssue("msg").onRange(26, 8, 26, 10);

    verifier.assertOneOrMoreIssues();
  }

  @Test
  public void several_issues_on_the_same_line() throws Exception {

    Path path = Paths.get("src/test/resources/several-issues-on-the-same-line.js");

    SingleFileVerifier verifier = SingleFileVerifier.create(path, UTF_8);
    CommentParser.create().addSingleLineCommentSyntax("//").parseInto(path, verifier);

    verifier.reportIssue("Error1").onLine(2);
    verifier.reportIssue("Error2").onLine(2);
    verifier.reportIssue("Error3").onLine(2);

    verifier.assertOneOrMoreIssues();
  }

  @Test
  public void report_when_issue_differences() throws Exception {

    Path path = Paths.get("src/test/resources/simple.js");

    SingleFileVerifier verifier = SingleFileVerifier.create(path, UTF_8);
    CommentParser.create().addSingleLineCommentSyntax("//").parseInto(path, verifier);

    verifier.reportIssue("RuLe MeSsAgE")
      .onRange(2,5,2,9);

    assertThatThrownBy(() -> verifier.assertOneOrMoreIssues())
      .isInstanceOf(ComparisonFailure.class)
      .hasMessageContaining(
        "[----------------------------------------------------------------------]\n" +
          "[ '-' means expected but not raised, '+' means raised but not expected ]\n" +
          "  <simple.js>\n" +
          "- 002: Noncompliant {{Rule message}}\n" +
          "+ 002: Noncompliant {{RuLe MeSsAgE}}\n" +
          "  002:     alert(msg);\n" +
          "- 002:           ^^^\n" +
          "+ 002:     ^^^^^\n" +
          "[----------------------------------------------------------------------]");
  }

  @Test
  public void message_order_does_not_matter() throws Exception {

    Path path = Paths.get("src/test/resources/several-issues-on-the-same-line.js");

    SingleFileVerifier verifier = SingleFileVerifier.create(path, UTF_8);
    CommentParser.create().addSingleLineCommentSyntax("//").parseInto(path, verifier);

    verifier.reportIssue("Error2").onLine(2);
    verifier.reportIssue("Error3").onLine(2);
    verifier.reportIssue("Error1").onLine(2);

    verifier.assertOneOrMoreIssues();
  }

  @Test
  public void primary_and_secondary_at_the_same_location() throws Exception {
    Path path = Paths.get("src/test/resources/same-location.js");

    SingleFileVerifier verifier = SingleFileVerifier.create(path, UTF_8);
    CommentParser.create().addSingleLineCommentSyntax("//").parseInto(path, verifier);

    verifier.reportIssue("Primary1")
      .onRange(2,5,2,7)
      .addSecondary(2, 5,2,7, "Secondary1");

    verifier.reportIssue("Primary2")
      .onRange(6,5,6,7)
      .addSecondary(6, 5,6,7, "Secondary2");

    verifier.assertOneOrMoreIssues();
  }
}
