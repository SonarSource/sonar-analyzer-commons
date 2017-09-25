/*
 * SonarQube Analyzer Test Commons
 * Copyright (C) 2009-2017 SonarSource SA
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
import org.junit.Test;

import static java.nio.charset.StandardCharsets.UTF_8;

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
      .addSingleLineCommentParser("//")
      .addMultiLineCommentParser("/*", "*/")
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

}
