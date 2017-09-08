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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.stream.Collectors;
import javax.annotation.Nullable;

public class LineIssues {

  public static final String COMMENT_PREFIX = " Noncompliant";

  private static final String EFFORT_TO_FIX = "effortToFix";

  public final TestFile testFile;

  public final int line;

  public final List<String> messages;

  public final Map<String, String> params;

  @Nullable
  public PrimaryLocation primaryLocation;

  public LineIssues(TestFile testFile, int line, String[] messages, Map<String, String> params, @Nullable PrimaryLocation primaryLocation) {
    this.testFile = testFile;
    this.line = line;
    this.messages = new ArrayList<>(Arrays.asList(messages));
    this.params = params;
    this.primaryLocation = primaryLocation;
  }

  public static LineIssues at(TestFile testFile, int line, @Nullable PrimaryLocation primaryLocation) {
    return new LineIssues(testFile, line, new String[0], new HashMap<>(), primaryLocation);
  }

  public void add(String message, @Nullable Double effortToFix) {
    if (!messages.isEmpty() && !Objects.equals(effortToFix, effortToFix())) {
      throw new IllegalStateException("At line " + line + " several issues with different 'effortToFix'.");
    }
    messages.add(message);
    if (effortToFix != null) {
      params.put(EFFORT_TO_FIX, effortToFix.toString());
    }
  }

  @Nullable
  public Double effortToFix() {
    String value = params.get(EFFORT_TO_FIX);
    return value == null ? null : Double.valueOf(value);
  }

  public LineIssues validateExpected() {
    if (primaryLocation != null) {
      int additionalCount = primaryLocation.secondaryAndFlowLocationCount();
      if (primaryLocation.expectedAdditionalCount == null && additionalCount > 0) {
        primaryLocation.expectedAdditionalCount = additionalCount;
      }
    }
    return this;
  }

  public LineIssues dropUntestedAttributes(@Nullable LineIssues other) {
    if (other == null) {
      return this;
    }
    if (other.messages.get(0) == null) {
      for (int i = 0; i < messages.size(); i++) {
        messages.set(i, null);
      }
    }
    if (other.primaryLocation == null) {
      primaryLocation = null;
    } else if (primaryLocation != null) {
      if (other.primaryLocation.expectedAdditionalCount == null) {
        primaryLocation.expectedAdditionalCount = null;
      }
      dropUntestedAttributes(primaryLocation.secondaryLocations, other.primaryLocation.secondaryLocations);
    }
    if (other.effortToFix() == null) {
      params.remove(EFFORT_TO_FIX);
    }
    return this;
  }

  private static void dropUntestedAttributes(List<SecondaryLocation> thisSecondaryLocations, List<SecondaryLocation> otherSecondaryLocations) {
    boolean secondaryWithoutIndex = !otherSecondaryLocations.isEmpty() &&
      otherSecondaryLocations.stream().allMatch(secondary -> secondary.index == null);
    if (secondaryWithoutIndex) {
      thisSecondaryLocations.forEach(secondary -> secondary.index = null);
    }
    for (int i = 0; i < otherSecondaryLocations.size(); i++) {
      SecondaryLocation otherLocation = otherSecondaryLocations.get(i);
      SecondaryLocation thisLocation = i < thisSecondaryLocations.size() ? thisSecondaryLocations.get(i) : null;
      if (thisLocation != null && otherLocation.message == null) {
        thisLocation.message = null;
      }
    }
  }

  @Override
  public String toString() {
    if (messages.isEmpty()) {
      return "";
    }
    StringBuilder out = new StringBuilder();
    appendLineNumber(out, line);
    out.append(testFile.commentPrefix);
    out.append(COMMENT_PREFIX);
    if (messages.size() > 1 && messages.get(0) == null) {
      out.append(" ").append(messages.size());
    } else if (messages.get(0) != null) {
      out.append(" {{");
      out.append(messages.stream().collect(Collectors.joining("}} {{")));
      out.append("}}");
    }
    Double effort = effortToFix();
    if (effort != null) {
      String displayedEffort = effort.toString().replaceFirst(".0$", "");
      out.append(" [[effortToFix=").append(displayedEffort).append("]]");
    }
    out.append("\n");
    appendLocations(out);
    return out.toString();
  }

  private static void appendLineNumber(StringBuilder out, int lineNumber) {
    if (lineNumber > 999) {
      throw new IllegalStateException();
    }
    out.append(String.format("%03d: ", lineNumber));
  }

  private void appendLocations(StringBuilder out) {
    if (primaryLocation != null) {
      List<PreciseLocation> locations = new ArrayList<>();
      locations.add(primaryLocation);
      if (primaryLocation.expectedAdditionalCount != null) {
        locations.addAll(primaryLocation.secondaryLocations);
        primaryLocation.flowLocations.forEach(locations::addAll);
        locations.sort(Comparator.comparing(location -> location.range));
      }
      Map<Integer, PerLineLocationWriter> writerPerLine = new TreeMap<>();
      for (PreciseLocation location : locations) {
        writerPerLine.computeIfAbsent(
          location.range.line, key -> new PerLineLocationWriter(String.format("%03d: ", key), reportLineAt(key))).add(location);
      }
      writerPerLine.values().forEach(writer -> writer.write(out));
    } else {
      appendLineNumber(out, line);
      out.append(reportLineAt(line));
    }
    out.append("\n");
  }

  private String reportLineAt(int lineNumber) {
    if (lineNumber < 0 || lineNumber > testFile.lines.length) {
      return "ERROR, no line " + lineNumber + " in " + testFile.name;
    } else if (lineNumber == 0) {
      return "<issue on file " + testFile.name + ">";
    } else {
      return testFile.lineWithoutComment(lineNumber);
    }
  }

}
