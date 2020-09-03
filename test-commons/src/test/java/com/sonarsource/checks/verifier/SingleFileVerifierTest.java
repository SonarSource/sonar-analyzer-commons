/*
 * SonarSource Analyzers Test Commons
 * Copyright (C) 2009-2020 SonarSource SA
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
package com.sonarsource.checks.verifier;

import java.nio.file.Path;
import java.nio.file.Paths;
import org.hamcrest.core.StringContains;
import org.junit.ComparisonFailure;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import static java.nio.charset.StandardCharsets.UTF_8;

public class SingleFileVerifierTest {

  @Rule
  public ExpectedException thrown = ExpectedException.none();

  @Test
  public void code_js_simple() throws Exception {
    Path path = Paths.get("src/test/resources/code.js");

    SingleFileVerifier verifier = SingleFileVerifier.create(path, UTF_8);

    verifier.addComment(4, 19, "// Noncompliant", 2, 0);

    verifier.reportIssue("issue").onLine(4);

    verifier.assertOneOrMoreIssues();
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

    thrown.expect(ComparisonFailure.class);
    thrown.expectMessage(StringContains.containsString(
        "[----------------------------------------------------------------------]\n" +
        "[ '-' means expected but not raised, '+' means raised but not expected ]\n" +
        "  <simple.js>\n" +
        "- 002: Noncompliant {{Rule message}}\n" +
        "+ 002: Noncompliant {{RuLe MeSsAgE}}\n" +
        "  002:     alert(msg);\n" +
        "- 002:           ^^^\n" +
        "+ 002:     ^^^^^\n" +
        "[----------------------------------------------------------------------]"));

    verifier.assertOneOrMoreIssues();
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
