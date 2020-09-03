/*
 * SonarSource Analyzers Test Commons
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

import com.sonarsource.checks.verifier.FileContent;
import java.nio.file.Path;
import java.util.List;

public class Comment {
  public final Path path;
  public final int line;
  public final int column;
  public final int contentColumn;
  public final String content;

  public Comment(Path path, int line, int column, int contentColumn, String content) {
    this.path = path;
    this.line = line;
    this.column = column;
    this.contentColumn = contentColumn;
    this.content = content;
  }

  @Override
  public String toString() {
    return "(" + path.getFileName() + "," + line + "," + column + "," + contentColumn + "," + content + ")";
  }

  public interface Parser {

    List<Comment> parse(FileContent file);

  }

}
