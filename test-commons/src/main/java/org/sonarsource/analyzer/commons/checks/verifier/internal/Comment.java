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

import java.nio.file.Path;
import java.util.List;
import org.sonarsource.analyzer.commons.checks.verifier.FileContent;

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
