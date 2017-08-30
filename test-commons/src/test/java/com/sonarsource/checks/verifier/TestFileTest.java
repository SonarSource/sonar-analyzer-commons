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
import org.hamcrest.CoreMatchers;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

public class TestFileTest {

  public static final Path MAIN_JS = Paths.get("src/test/resources/main.js");

  @Rule
  public ExpectedException thrown = ExpectedException.none();

  @Test
  public void constructor() {
    TestFile file = TestFile.read(MAIN_JS, UTF_8, "//");
    assertThat(file.name).isEqualTo("main.js");
    assertThat(file.commentPrefix).isEqualTo("//");
    assertThat(file.content).startsWith("function main()");
    assertThat(file.lines).containsExactly(
      "function main() {",
      "  alert('Hello'); // display hello",
      "}",
      "");
    assertThat(file.line(2)).isEqualTo("  alert('Hello'); // display hello");
    assertThat(file.line(3)).isEqualTo("}");
    assertThat(file.lineWithoutComment(2)).isEqualTo("  alert('Hello');");
    assertThat(file.lineWithoutComment(3)).isEqualTo("}");
    assertThat(file.commentAt(2)).isEqualTo("// display hello");
    assertThat(file.commentAt(3)).isNull();
  }

  @Test
  public void invalid_file_path() {
    thrown.expect(IllegalStateException.class);
    thrown.expectMessage(CoreMatchers.startsWith("Failed to read 'invalid.js':"));
    TestFile.read(Paths.get("bad/invalid.js"), UTF_8, "//");
  }

  @Test
  public void invalid_positive_line() {
    TestFile file = new TestFile("file.cpp", "//", "int a;\nint b;\n");
    thrown.expect(IllegalStateException.class);
    thrown.expectMessage("No line 5 in file.cpp");
    file.line(5);
  }

  @Test
  public void invalid_negative_line() {
    TestFile file = new TestFile("file.cpp", "//", "int a;\nint b;\n");
    thrown.expect(IllegalStateException.class);
    thrown.expectMessage("No line -2 in file.cpp");
    file.line(-2);
  }

}
