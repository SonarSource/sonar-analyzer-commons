/*
 * SonarSource Analyzers Test Commons
 * Copyright (C) 2009-2021 SonarSource SA
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

public class CommentTest {
  @Test
  public void constructor() throws Exception {
    Comment comment = new Comment(Paths.get("dir/file.js"),1, 2, 3, "Error");
    assertThat(comment.path).isEqualTo(Paths.get("dir/file.js"));
    assertThat(comment.line).isOne();
    assertThat(comment.column).isEqualTo(2);
    assertThat(comment.contentColumn).isEqualTo(3);
    assertThat(comment.content).isEqualTo("Error");
    assertThat(comment).hasToString("(file.js,1,2,3,Error)");
  }
}
