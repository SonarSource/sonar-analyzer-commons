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
package com.sonarsource.checks.verifier.internal;

import com.sonarsource.checks.coverage.UtilityClass;
import com.sonarsource.checks.verifier.FileContent;
import java.nio.file.Paths;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import static com.sonarsource.checks.verifier.internal.NoncompliantCommentParser.parse;
import static org.assertj.core.api.Assertions.assertThat;

public class NoncompliantCommentParserTest {

  @Rule
  public ExpectedException thrown = ExpectedException.none();

  private TestFile file = new TestFile(new FileContent(Paths.get("source_code"), "\n\n\n\n"));

  @Test
  public void constructor() throws Exception {
    UtilityClass.assertGoodPractice(NoncompliantCommentParser.class);
  }

  @Test
  public void ignored_comment() throws Exception {
    LineIssues issues = parse(file, 1, "bla bla bla");
    assertThat(issues).isNull();
  }

  @Test
  public void invalid_comment() throws Exception {
    thrown.expect(IllegalStateException.class);
    thrown.expectMessage("Invalid comment format line 1: Noncompliant Noncompliant");
    parse(file, 1, "Noncompliant Noncompliant");
  }

  @Test
  public void params() throws Exception {
    LineIssues issues = parse(file, 1, " Noncompliant [[param1=2;param2]]");
    assertThat(issues).isNotNull();
    assertThat(issues.params.keySet()).containsExactly("param1", "param2");
    assertThat(issues.params.get("param1")).isEqualTo("2");
    assertThat(issues.params.get("param2")).isEqualTo("");
  }

  @Test
  public void issue_count() throws Exception {
    LineIssues issues = parse(file, 1, " Noncompliant 2");
    assertThat(issues).isNotNull();
    assertThat(issues.messages).hasSize(2);
    assertThat(issues.messages.get(0)).isNull();
    assertThat(issues.messages.get(1)).isNull();
  }

  @Test
  public void messages() throws Exception {
    LineIssues issues = parse(file, 1, " Noncompliant {{msg1}} {{msg2}} {{msg3}}");
    assertThat(issues).isNotNull();
    assertThat(issues.messages).hasSize(3);
    assertThat(issues.messages.get(0)).isEqualTo("msg1");
    assertThat(issues.messages.get(1)).isEqualTo("msg2");
    assertThat(issues.messages.get(2)).isEqualTo("msg3");
  }

  @Test
  public void issue_count_and_messages() throws Exception {
    thrown.expect(IllegalStateException.class);
    thrown.expectMessage("Error, you can not specify issue count and messages at line 1, you have to choose either: \n"
      + "  Noncompliant 3\n"
      + "or\n"
      + "  Noncompliant {{msg1}} {{msg2}} {{msg3}}");
    parse(file, 1, " Noncompliant 3 {{msg1}} {{msg2}} {{msg3}}");
  }

  @Test
  public void support_comment_with_newLine() throws Exception {
    LineIssues issues = parse(file, 1, " Noncompliant\n");
    assertThat(issues).isNotNull();
    assertThat(issues.messages).hasSize(1);
    issues = parse(file, 1, " Noncompliant\r");
    assertThat(issues).isNotNull();
    assertThat(issues.messages).hasSize(1);
    issues = parse(file, 1, " Noncompliant\r\n");
    assertThat(issues).isNotNull();
    assertThat(issues.messages).hasSize(1);
  }

}
