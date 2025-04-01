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
import org.junit.Test;
import org.sonarsource.analyzer.commons.checks.coverage.UtilityClass;
import org.sonarsource.analyzer.commons.checks.verifier.FileContent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.sonarsource.analyzer.commons.checks.verifier.internal.NoncompliantCommentParser.parse;

public class NoncompliantCommentParserTest {

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
    assertThatThrownBy(() -> parse(file, 1, "Noncompliant Noncompliant"))
      .isInstanceOf(IllegalStateException.class)
      .hasMessage("Invalid comment format line 1: Noncompliant Noncompliant");
  }

  @Test
  public void params() throws Exception {
    LineIssues issues = parse(file, 1, " Noncompliant [[param1=2;param2]]");
    assertThat(issues).isNotNull();
    assertThat(issues.params)
      .containsOnlyKeys("param1", "param2")
      .containsEntry("param1", "2")
      .containsEntry("param2", "");
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
    assertThat(issues.messages)
      .hasSize(3)
      .containsExactly("msg1", "msg2", "msg3");
  }

  @Test
  public void issue_count_and_messages() throws Exception {
    assertThatThrownBy(() -> parse(file, 1, " Noncompliant 3 {{msg1}} {{msg2}} {{msg3}}"))
      .isInstanceOf(IllegalStateException.class)
      .hasMessage("Error, you can not specify issue count and messages at line 1, you have to choose either: \n"
        + "  Noncompliant 3\n"
        + "or\n"
        + "  Noncompliant {{msg1}} {{msg2}} {{msg3}}\n");
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
