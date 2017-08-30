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

import org.junit.Test;

import static com.sonarsource.checks.verifier.NoncompliantCommentParser.parse;
import static org.assertj.core.api.Assertions.assertThat;

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
        + "002: # Noncompliant\n"
        + "002: cd folder\n");
    assertMatch("//",
      "a++; // Noncompliant {{error}}", 1, ""
        + "001: // Noncompliant {{error}}\n"
        + "001: a++;\n");
    assertMatch("//",
      "a++; // Noncompliant {{error1}} {{error2}}", 1, ""
        + "001: // Noncompliant {{error1}} {{error2}}\n"
        + "001: a++;\n");
    assertMatch("//",
      "a++; // Noncompliant 3", 1, ""
        + "001: // Noncompliant 3\n"
        + "001: a++;\n");
    assertMatch("--",
      "\nEXEC f\n-- Noncompliant@2", 3, ""
        + "002: -- Noncompliant\n"
        + "002: EXEC f\n");
    assertMatch("--",
      "\n-- Noncompliant@+1\nEXEC f\n", 2, ""
        + "003: -- Noncompliant\n"
        + "003: EXEC f\n");
    assertMatch("--",
      "\nEXEC f\n\n-- Noncompliant@-2", 4, ""
        + "002: -- Noncompliant\n"
        + "002: EXEC f\n");
    assertMatch("//",
      "i++; // Noncompliant {{error}} [[effortToFix=2]]", 1, ""
        + "001: // Noncompliant {{error}} [[effortToFix=2]]\n"
        + "001: i++;\n");
  }

  private static void assertMatch(String commentPrefix, String code, int line, String expected) {
    TestFile file = new TestFile("source_code", commentPrefix, code);
    String comment = file.commentAt(line);
    assertThat(comment).as("Comment at line " + line).isNotNull();
    String commentContent = comment.substring(commentPrefix.length());
    LineIssues lineIssues = parse(file, line, commentContent);
    assertThat(lineIssues == null ? "<null>" : lineIssues.toString()).isEqualTo(expected);
  }

}
