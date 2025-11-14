/*
 * SonarSource Analyzers Test Commons
 * Copyright (C) 2009-2025 SonarSource Sàrl
 * mailto:info AT sonarsource DOT com
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the Sonar Source-Available License Version 1, as published by SonarSource Sàrl.
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

import static org.assertj.core.api.Assertions.assertThat;

public class IssueLocationTest {
  @Test
  public void file() throws Exception {
    IssueLocation location = new IssueLocation.File(Paths.get("file.js"));
    assertThat(location.getType()).isEqualTo(IssueLocation.Type.FILE);
    assertThat(location.getSourcePath()).hasToString("file.js");
  }

  @Test
  public void line() throws Exception {
    IssueLocation.Line location = new IssueLocation.Line(Paths.get("file.js"), 42);
    assertThat(location.getType()).isEqualTo(IssueLocation.Type.LINE);
    assertThat(location.getSourcePath()).hasToString("file.js");
    assertThat(location.getLine()).isEqualTo(42);
  }

  @Test
  public void range() throws Exception {
    IssueLocation.Range location = new IssueLocation.Range(Paths.get("file.js"), 42, 12, 43, 21);
    assertThat(location.getType()).isEqualTo(IssueLocation.Type.RANGE);
    assertThat(location.getSourcePath()).hasToString("file.js");
    assertThat(location.getLine()).isEqualTo(42);
    assertThat(location.getColumn()).isEqualTo(12);
    assertThat(location.getEndLine()).isEqualTo(43);
    assertThat(location.getEndColumn()).isEqualTo(21);
  }
}
