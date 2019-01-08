/*
 * SonarQube Analyzer Test Commons
 * Copyright (C) 2009-2019 SonarSource SA
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

public class MultiFileVerifierTest {

  @Rule
  public ExpectedException thrown = ExpectedException.none();


  @Test
  public void code_js_simple() throws Exception {
    Path path = Paths.get("src/test/resources/code.js");

    MultiFileVerifier verifier = MultiFileVerifier.create(path, UTF_8);

    verifier.addComment(path, 4, 19, "// Noncompliant", 2, 0);

    verifier.reportIssue(path, "issue").onLine(4);

    verifier.assertOneOrMoreIssues();
  }

  @Test
  public void code_js_with_comment_parser() throws Exception {
    Path path = Paths.get("src/test/resources/code.js");

    MultiFileVerifier verifier = MultiFileVerifier.create(path, UTF_8);

    CommentParser.create()
      .addSingleLineCommentSyntax("//")
      .parseInto(path, verifier);

    verifier.reportIssue(path, "Issue on file").onFile();
    verifier.reportIssue(path, "issue1").onLine(4);
    verifier.reportIssue(path, "issue2").onLine(4);

    verifier.reportIssue(path, "msg").onRange(9, 11, 9, 13)
      .addSecondary(path, 6, 9, 6, 11, "msg");

    verifier.reportIssue(path, "Rule message").onRange(12, 5, 12, 9)
      .addSecondary(path, 12, 10, 12, 18, "Secondary location message1")
      .addSecondary(path, 16, 5, 16, 9, "Secondary location message2");

    verifier.reportIssue(path, "Error").onRange(19, 5, 19, 9)
      .withGap(2.5d);

    verifier.reportIssue(path, "msg").onRange(22, 5, 22, 9)
      .addSecondary(path, 22, 12, 22, 16, "msg");

    verifier.reportIssue(path, "msg").onRange(26, 8, 26, 10);

    verifier.assertOneOrMoreIssues();
  }

  @Test
  public void code_js_without_comment_parser() throws Exception {
    Path path = Paths.get("src/test/resources/code.js");

    MultiFileVerifier verifier = MultiFileVerifier.create(path, UTF_8);

    verifier.addComment(path, 1, 1, "// ignored comment", 2, 0);
    verifier.addComment(path, 2, 1, "/* multiline comment */", 2, 2);
    verifier.addComment(path, 4, 19, "// Noncompliant 2", 2, 0);
    verifier.addComment(path, 7, 5, "//  ^^^>", 2, 0);
    verifier.addComment(path, 9, 17, "// Noncompliant", 2, 0);
    verifier.addComment(path, 10, 5, "//    ^^^ 1", 2, 0);
    verifier.addComment(path, 12, 20, "// Noncompliant {{Rule message}}", 2, 0);
    verifier.addComment(path, 13, 2, "// ^^^^^ 2", 2, 0);
    verifier.addComment(path, 14, 2, "//      ^^^^^^^^^@-1< {{Secondary location message1}}", 2, 0);
    verifier.addComment(path, 17, 2, "// ^^^^^< {{Secondary location message2}}", 2, 0);
    verifier.addComment(path, 19, 17, "// Noncompliant {{Error}} [[effortToFix=2.5]]", 2, 0);
    verifier.addComment(path, 20, 2, "// ^^^^^", 2, 0);
    verifier.addComment(path, 22, 21, "// Noncompliant", 2, 0);
    verifier.addComment(path, 27, 1, "// Noncompliant@0 {{Issue on file}}", 2, 0);

    verifier.reportIssue(path, "Issue on file").onFile();
    verifier.reportIssue(path, "issue1").onLine(4);
    verifier.reportIssue(path, "issue2").onLine(4);

    verifier.reportIssue(path, "msg").onRange(9, 11, 9, 13)
      .addSecondary(path, 6, 9, 6, 11, "msg");

    verifier.reportIssue(path, "Rule message").onRange(12, 5, 12, 9)
      .addSecondary(path, 12, 10, 12, 18, "Secondary location message1")
      .addSecondary(path, 16, 5, 16, 9, "Secondary location message2");

    verifier.reportIssue(path, "Error").onRange(19, 5, 19, 9)
      .withGap(2.5d);

    verifier.reportIssue(path, "msg").onRange(22, 5, 22, 9)
      .addSecondary(path, 22, 12, 22, 16, "msg");

    verifier.assertOneOrMoreIssues();
  }

  @Test
  public void code_js_without_issue() throws Exception {
    Path path = Paths.get("src/test/resources/code.js");
    MultiFileVerifier verifier = MultiFileVerifier.create(path, UTF_8);
    // no addComment(...)
    // no reportIssue(...)
    verifier.assertNoIssues();

    thrown.expect(ComparisonFailure.class);
    thrown.expectMessage(StringContains.containsString(
      "ERROR: 'assertOneOrMoreIssues()' is called but there's no 'Noncompliant' comments."));
    verifier.assertOneOrMoreIssues();
  }

  @Test
  public void code_js_with_one_issue() throws Exception {
    Path path = Paths.get("src/test/resources/code.js");
    MultiFileVerifier verifier = MultiFileVerifier.create(path, UTF_8);

    verifier.addComment(path, 4, 19, "// Noncompliant", 2, 0);
    verifier.reportIssue(path, "issue").onLine(4);

    verifier.assertOneOrMoreIssues();

    thrown.expect(ComparisonFailure.class);
    thrown.expectMessage(StringContains.containsString(
      "ERROR: 'assertNoIssues()' is called but there's some 'Noncompliant' comments."));
    verifier.assertNoIssues();
  }

  @Test
  public void code_js_with_gap() throws Exception {
    Path path = Paths.get("src/test/resources/code.js");

    MultiFileVerifier verifier = MultiFileVerifier.create(path, UTF_8);

    verifier.addComment(path, 4, 19, "// Noncompliant [[effortToFix=2.5]]", 2, 0);

    verifier.reportIssue(path, "issue").onLine(4).withGap(2.5d);

    verifier.assertOneOrMoreIssues();
  }

}
