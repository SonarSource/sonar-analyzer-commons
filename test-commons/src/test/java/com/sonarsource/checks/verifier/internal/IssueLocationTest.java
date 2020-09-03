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
package com.sonarsource.checks.verifier.internal;

import java.nio.file.Paths;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class IssueLocationTest {
  @Test
  public void file() throws Exception {
    IssueLocation location = new IssueLocation.File(Paths.get("file.js"));
    assertThat(location.getType()).isEqualTo(IssueLocation.Type.FILE);
    assertThat(location.getSourcePath().toString()).isEqualTo("file.js");
  }

  @Test
  public void line() throws Exception {
    IssueLocation.Line location = new IssueLocation.Line(Paths.get("file.js"), 42);
    assertThat(location.getType()).isEqualTo(IssueLocation.Type.LINE);
    assertThat(location.getSourcePath().toString()).isEqualTo("file.js");
    assertThat(location.getLine()).isEqualTo(42);
  }

  @Test
  public void range() throws Exception {
    IssueLocation.Range location = new IssueLocation.Range(Paths.get("file.js"), 42, 12, 43, 21);
    assertThat(location.getType()).isEqualTo(IssueLocation.Type.RANGE);
    assertThat(location.getSourcePath().toString()).isEqualTo("file.js");
    assertThat(location.getLine()).isEqualTo(42);
    assertThat(location.getColumn()).isEqualTo(12);
    assertThat(location.getEndLine()).isEqualTo(43);
    assertThat(location.getEndColumn()).isEqualTo(21);
  }
}
