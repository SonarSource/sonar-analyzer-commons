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

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import javax.annotation.Nullable;

public class PerLineLocationWriter {

  private final int indent;

  private final String linePrefix;

  private final List<PreciseLocation> locations = new ArrayList<>();

  private final List<StringBuilder> outputLines = new ArrayList<>();

  public PerLineLocationWriter(String lineNumber, String sourceCodeLine) {
    indent = lineNumber.length();
    linePrefix = lineNumber;
    outputLines.add(new StringBuilder().append(lineNumber).append(sourceCodeLine));
  }

  public void add(PreciseLocation location) {
    locations.add(location);
  }

  public void write(StringBuilder out, @Nullable UnderlinedRange primaryRange) {
    locations.sort(Comparator.comparing(location -> location.range));
    for (PreciseLocation location : locations) {
      boolean primaryIsWritten = primaryRange != null && primaryRange.compareTo(location.range) <= 0;
      location.write(indent, availableStringBuilder(location), primaryIsWritten);
    }
    for (StringBuilder locationLine : outputLines) {
      out.append(locationLine).append('\n');
    }
  }

  private StringBuilder availableStringBuilder(PreciseLocation location) {
    int column = indent + location.range.column;
    int availableLineIndex = 1;
    while (availableLineIndex < outputLines.size() && !isAvailable(column, outputLines.get(availableLineIndex))) {
      availableLineIndex++;
    }
    if (availableLineIndex >= outputLines.size()) {
      StringBuilder newLine = new StringBuilder();
      newLine.append(linePrefix);
      outputLines.add(newLine);
      return newLine;
    } else {
      return outputLines.get(availableLineIndex);
    }
  }

  private static boolean isAvailable(int column, StringBuilder line) {
    int index = column - 1;
    return line.length() < index ||
      (line.length() == index && line.charAt(index - 1) != '^');
  }

}
