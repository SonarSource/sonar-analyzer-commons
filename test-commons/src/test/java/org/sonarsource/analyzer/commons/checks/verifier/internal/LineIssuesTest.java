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
package org.sonarsource.analyzer.commons.checks.verifier.internal;

import java.nio.file.Paths;
import java.util.List;
import java.util.Locale;
import org.junit.Test;
import org.sonarsource.analyzer.commons.checks.verifier.FileContent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.sonarsource.analyzer.commons.checks.verifier.internal.NoncompliantCommentParser.parse;

public class LineIssuesTest {

  @Test
  public void parse_and_print() throws Exception {
    assertMatch("//",
      "a++; //", 1, "<null>");
    assertMatch("#",
      "cd folder # Compliant", 1, "<null>");
    assertMatch("#",
      "ls\n"
        + "cd folder # Noncompliant",
      2, ""
        + "002: Noncompliant\n"
        + "002: cd folder\n");
    assertMatch("//",
      "a++; // Noncompliant {{error}}", 1, ""
        + "001: Noncompliant {{error}}\n"
        + "001: a++;\n");
    assertMatch("//",
      "a++; // Noncompliant {{error1}} {{error2}}", 1, ""
        + "001: Noncompliant {{error1}} {{error2}}\n"
        + "001: a++;\n");
    assertMatch("//",
      "a++; // Noncompliant 3", 1, ""
        + "001: Noncompliant 3\n"
        + "001: a++;\n");
    assertMatch("--",
      "\nEXEC f\n-- Noncompliant@2", 3, ""
        + "002: Noncompliant\n"
        + "002: EXEC f\n");
    assertMatch("--",
      "\n-- Noncompliant@+1\nEXEC f\n", 2, ""
        + "003: Noncompliant\n"
        + "003: EXEC f\n");
    assertMatch("--",
      "\nEXEC f\n\n-- Noncompliant@-2", 4, ""
        + "002: Noncompliant\n"
        + "002: EXEC f\n");
    assertMatch("//",
      "i++; // Noncompliant {{error}} [[effortToFix=2]]", 1, ""
        + "001: Noncompliant {{error}} [[effortToFix=2]]\n"
        + "001: i++;\n");
  }

  @Test
  public void use_another_locale() throws Exception {
    Locale.setDefault(Locale.FRANCE);
    assertMatch("//",
      "i++; // Noncompliant {{error}} [[effortToFix=2.5]]", 1, ""
        + "001: Noncompliant {{error}} [[effortToFix=2.5]]\n"
        + "001: i++;\n");
  }

  private static void assertMatch(String commentPrefix, String code, int line, String expected) {
    TestFile file = new TestFile(new FileContent(Paths.get("source_code").toAbsolutePath(), code));
    List<Comment> comments = TestFileTest.parseComments(commentPrefix, file);
    for (Comment comment : comments) {
      file.addNoncompliantComment(comment);
    }
    Comment comment = comments.stream().filter(c -> c.line == line).findFirst().orElse(null);
    assertThat(comment).as("Comment at line " + line).isNotNull();
    LineIssues lineIssues = parse(file, line, comment.content);
    assertThat(lineIssues == null ? "<null>" : lineIssues.toString()).isEqualTo(expected);
  }

}
