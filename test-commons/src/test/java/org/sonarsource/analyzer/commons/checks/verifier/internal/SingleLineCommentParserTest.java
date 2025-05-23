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

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import org.junit.Test;
import org.sonarsource.analyzer.commons.checks.verifier.FileContent;

import static org.assertj.core.api.Assertions.assertThat;

public class SingleLineCommentParserTest {

  private Path path = Paths.get("file.js");
  private SingleLineCommentParser parser = new SingleLineCommentParser("//");

  @Test
  public void parse() throws Exception {
    List<Comment> comments = parser.parse(new FileContent(path, "" +
      "abc // Comment1\n" +
      "def\n" +
      "// Comment2\r" +
      "ghi // Comment3\r\n" +
      "// Comment4"));

    assertThat(comments).hasSize(4);
    assertThat(comments.get(0)).hasToString("(file.js,1,5,7, Comment1)");
    assertThat(comments.get(1)).hasToString("(file.js,3,1,3, Comment2)");
    assertThat(comments.get(2)).hasToString("(file.js,4,5,7, Comment3)");
    assertThat(comments.get(3)).hasToString("(file.js,5,1,3, Comment4)");
  }

  @Test
  public void no_comment() throws Exception {
    assertThat(parser.parse(new FileContent(path, ""))).isEmpty();
    assertThat(parser.parse(new FileContent(path, "abc\ndef"))).isEmpty();
  }

}
