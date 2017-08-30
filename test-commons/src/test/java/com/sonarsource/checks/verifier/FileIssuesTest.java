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
import static org.assertj.core.api.Assertions.assertThat;

public class FileIssuesTest {

  public static final Path CODE_JS = Paths.get("src/test/resources/code.js");
  public static final Path CODE_ISSUES_JS = Paths.get("src/test/resources/code.issues.js");

  @Test
  public void parse() throws Exception {
    TestFile codeFile = TestFile.read(CODE_JS, UTF_8, "//");
    TestFile expectedIssues = TestFile.read(CODE_ISSUES_JS, UTF_8, "//");
    FileIssues fileIssues = new FileIssues(codeFile);
    String[] lines = codeFile.content.split("\n");
    for (int line = 1; line <= lines.length; line++) {
      String lineOfCode = lines[line - 1];
      int commentStart = Math.max(lineOfCode.indexOf("//"),lineOfCode.indexOf("/*"));
      if (commentStart != -1) {
        int column = commentStart + 1;
        fileIssues.addComment(line, column, lineOfCode.substring(commentStart));
      }
    }
    FileIssues.Report report = fileIssues.report();
    assertThat(report.expected).isEqualTo(expectedIssues.content);
  }

}
