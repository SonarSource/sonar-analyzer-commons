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
package com.sonarsource.checks.verifier.internal;

import com.sonarsource.checks.verifier.FileContent;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class MultiLineCommentParserTest {

  private Path path = Paths.get("file.js");
  private MultiLineCommentParser parser = new MultiLineCommentParser("/*", "*/");

  @Test
  public void parse() throws Exception {
    List<Comment> comments = parser.parse(new FileContent(path, "" +
      "abc /*Comment1*/\n" +
      "def /*Comment2\r" +
      "...*/\r\n" +
      "/*Comment3*/"));

    assertThat(comments).hasSize(3);
    assertThat(comments.get(0).toString()).isEqualTo("(file.js,1,5,7,Comment1)");
    assertThat(comments.get(1).toString()).isEqualTo("(file.js,2,5,7,Comment2\r...)");
    assertThat(comments.get(2).toString()).isEqualTo("(file.js,4,1,3,Comment3)");
  }

  @Test
  public void no_comment() throws Exception {
    assertThat(parser.parse(new FileContent(path, ""))).isEmpty();
    assertThat(parser.parse(new FileContent(path, "abc\ndef"))).isEmpty();
  }

  @Test(expected = IllegalStateException.class)
  public void missing_comment_suffix() throws Exception {
    parser.parse(new FileContent(path, "abc /* Comment"));
  }

}
