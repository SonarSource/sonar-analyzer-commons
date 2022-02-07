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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.Test;
import org.sonarsource.analyzer.commons.checks.verifier.FileContent;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class TestFileTest {

  public static final Path MAIN_JS = Paths.get("src/test/resources/main.js");

  @Test
  public void constructor() {
    TestFile file = new TestFile(new FileContent(MAIN_JS, UTF_8));
    file.addNoncompliantComment(new Comment(file.getPath(), 2, 19, 21, " Noncompliant"));
    file.addNoncompliantComment(new Comment(file.getPath(), 2, 27, 29, "liant"));
    assertThat(file.getName()).isEqualTo("main.js");
    assertThat(file.getContent()).startsWith("function main()");
    assertThat(file.getLines()).containsExactly(
      "function main() {",
      "  alert('Hello'); // Noncompliant",
      "}",
      "");
    assertThat(file.line(2)).isEqualTo("  alert('Hello'); // Noncompliant");
    assertThat(file.line(3)).isEqualTo("}");
    assertThat(file.lineWithoutNoncompliantComment(2)).isEqualTo("  alert('Hello');");
    assertThat(file.lineWithoutNoncompliantComment(3)).isEqualTo("}");
    assertThat(file.commentAt(2)).isEqualTo("// Noncompliant");
    assertThat(file.commentAt(3)).isNull();
  }

  @Test
  public void invalid_positive_line() {
    TestFile file = new TestFile(new FileContent(Paths.get("file.cpp"), "int a;\nint b;\n"));

    assertThatThrownBy(() -> file.line(5))
      .isInstanceOf(IllegalStateException.class)
      .hasMessage("No line 5 in file.cpp");
  }

  @Test
  public void invalid_negative_line() {
    TestFile file = new TestFile(new FileContent(Paths.get("file.cpp"), "int a;\nint b;\n"));

    assertThatThrownBy(() -> file.line(-2))
      .isInstanceOf(IllegalStateException.class)
      .hasMessage("No line -2 in file.cpp");
  }

  @Test
  public void comment_with_wrong_path() {
    TestFile file = new TestFile(new FileContent(Paths.get("file.cpp"), "int a;\nint b;\n"));
    Comment comment = new Comment(Paths.get("file2.cpp"), 2, 19, 21, " Noncompliant");

    assertThatThrownBy(() -> file.addNoncompliantComment(comment))
      .isInstanceOf(IllegalStateException.class)
      .hasMessageStartingWith("This comment is not related to file");
  }

  static List<Comment> parseComments(String prefix, FileContent file) {
    List<Comment> comments = new ArrayList<>();
    String[] lines = file.getLines();
    for (int line = 1; line <= lines.length; line++) {
      String lineOfCode = lines[line - 1];
      int commentStart = lineOfCode.indexOf(prefix);
      if (commentStart != -1) {
        int column = commentStart + 1;
        comments.add(new Comment(file.getPath().toAbsolutePath(),
          line, column, column + prefix.length(), lineOfCode.substring(commentStart + prefix.length())));
      }
    }
    return comments;
  }

  @Test
  public void line_prefix_small_file() {
    TestFile file = new TestFile(new FileContent(Paths.get("file.cpp"), "int a;\nint b;\n"));
    assertThat(file.linePrefix(2)).isEqualTo("002: ");
  }

  @Test
  public void line_prefix_big_file() {
    String fileContentWith1200Lines = String.join("\n", Collections.nCopies(1200, ""));
    TestFile file = new TestFile(new FileContent(Paths.get("file.cpp"), fileContentWith1200Lines));
    assertThat(file.linePrefix(2)).isEqualTo("0002: ");
  }

}
