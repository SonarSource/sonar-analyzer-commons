/*
 * SonarSource Analyzers Test Commons
 * Copyright (C) 2009-2022 SonarSource SA
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
package org.sonarsource.analyzer.commons.checks.verifier.internal;

import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.Test;
import org.sonarsource.analyzer.commons.checks.verifier.FileContent;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

public class FileIssuesTest {

  public static final Path CODE_JS = Paths.get("src/test/resources/code.js");
  public static final Path CODE_ISSUES_JS = Paths.get("src/test/resources/code.js.issues.txt");

  @Test
  public void parse() throws Exception {
    TestFile codeFile = new TestFile(new FileContent(CODE_JS.toAbsolutePath(), UTF_8));
    FileIssues fileIssues = new FileIssues(codeFile, TestFileTest.parseComments("//", codeFile));

    fileIssues.addActualIssue(0, "Issue on file", null);

    fileIssues.addActualIssue(4, "issue1", null);
    fileIssues.addActualIssue(4, "issue2", null);

    PrimaryLocation primary1 = new PrimaryLocation(new UnderlinedRange(9, 11, 9, 13), 1);
    primary1.addSecondary(new UnderlinedRange(6, 9, 6, 11), "msg");
    fileIssues.addActualIssue(9, "msg", primary1);

    PrimaryLocation primary2 = new PrimaryLocation(new UnderlinedRange(12, 5, 12, 9), 2);
    primary2.addSecondary(new UnderlinedRange(12, 10, 12, 18), "Secondary location message1");
    primary2.addSecondary(new UnderlinedRange(16, 5, 16, 9), "Secondary location message2");
    fileIssues.addActualIssue(12, "Rule message", primary2);

    PrimaryLocation primary3 = new PrimaryLocation(new UnderlinedRange(19, 5, 19, 9), 0);
    fileIssues.addActualIssue(19, "Error", primary3, 2.5d);

    PrimaryLocation primary4 = new PrimaryLocation(new UnderlinedRange(22, 5, 22, 9), 1);
    primary4.addSecondary(new UnderlinedRange(22, 12, 22, 16), "msg");
    fileIssues.addActualIssue(22, "msg", primary4);

    PrimaryLocation primary5 = new PrimaryLocation(new UnderlinedRange(26, 8, 26, 10), null);
    fileIssues.addActualIssue(26, "Error", primary5);

    FileContent expectedIssues = new FileContent(CODE_ISSUES_JS);
    Report report = fileIssues.report();
    assertThat(report.getExpectedIssueCount()).isEqualTo(8);
    assertThat(report.getActual()).isEqualTo(report.getExpected());
    assertThat(report.getActual()).isEqualTo(expectedIssues.getContent().replaceAll("\r\n", "\n"));
  }

}
