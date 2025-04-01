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

public interface IssueLocation {

  enum Type {
    FILE, LINE, RANGE
  }

  Path getSourcePath();

  Type getType();

  class File implements IssueLocation {

    private final Path sourcePath;

    public File(Path sourcePath) {
      this.sourcePath = sourcePath;
    }

    @Override
    public Path getSourcePath() {
      return sourcePath;
    }

    @Override
    public Type getType() {
      return Type.FILE;
    }
  }

  class Line extends File {

    private final int lineNumber;

    public Line(Path sourcePath, int lineNumber) {
      super(sourcePath);
      this.lineNumber = lineNumber;
    }

    public int getLine() {
      return lineNumber;
    }

    @Override
    public Type getType() {
      return Type.LINE;
    }
  }

  class Range extends Line {

    private final int column;
    private final int endLine;
    private final int endColumn;

    public Range(Path sourcePath, int line, int column, int endLine, int endColumn) {
      super(sourcePath, line);
      this.column = column;
      this.endLine = endLine;
      this.endColumn = endColumn;
    }

    public int getColumn() {
      return column;
    }

    public int getEndLine() {
      return endLine;
    }

    public int getEndColumn() {
      return endColumn;
    }

    @Override
    public Type getType() {
      return Type.RANGE;
    }
  }
}
