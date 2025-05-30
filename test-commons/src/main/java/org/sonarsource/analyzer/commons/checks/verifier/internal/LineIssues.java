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

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import javax.annotation.Nullable;
import org.sonarsource.analyzer.commons.checks.verifier.quickfix.QuickFix;

public class LineIssues {

  public static final String COMMENT_PREFIX = "Noncompliant";

  private static final String EFFORT_TO_FIX = "effortToFix";

  public final TestFile testFile;

  public final int line;

  public final List<String> messages;

  public final Map<String, String> params;

  private List<QuickFix> quickfixes = new ArrayList<>();

  @Nullable
  public PrimaryLocation primaryLocation;

  public LineIssues(TestFile testFile, int line, String[] messages, Map<String, String> params, @Nullable PrimaryLocation primaryLocation) {
    this.testFile = testFile;
    this.line = line;
    this.messages = new ArrayList<>(Arrays.asList(messages));
    this.params = params;
    this.primaryLocation = primaryLocation;
  }

  void merge(LineIssues other) {
    messages.addAll(other.messages);
    params.putAll(other.params);
    if (primaryLocation == null) {
      primaryLocation = other.primaryLocation;
    }
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
    out.append(COMMENT_PREFIX);
    boolean oneMessageIsMissing = messages.stream().filter(Objects::isNull).count() > 0;
    if (oneMessageIsMissing && messages.size() > 1) {
      out.append(" ").append(messages.size());
    }
    messages.stream()
      .filter(Objects::nonNull)
      .sorted()
      .forEach(message -> out.append(" {{").append(message).append("}}"));
    Double effort = effortToFix();
    if (effort != null) {
      DecimalFormat effortToFixFormat = new DecimalFormat("0.##", new DecimalFormatSymbols(Locale.ENGLISH));
      out.append(" [[effortToFix=").append(effortToFixFormat.format(effort)).append("]]");
    }
    out.append("\n");
    appendLocations(out);
    return out.toString();
  }

  private void appendLineNumber(StringBuilder out, int lineNumber) {
    out.append(testFile.linePrefix(lineNumber));
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
          location.range.line, key -> new PerLineLocationWriter(testFile.linePrefix(key), reportLineAt(key))).add(location);
      }
      writerPerLine.values().forEach(writer -> writer.write(out, primaryLocation.range));
    } else {
      appendLineNumber(out, line);
      out.append(reportLineAt(line));
    }
    out.append("\n");
  }

  private String reportLineAt(int lineNumber) {
    if (lineNumber < 0 || lineNumber > testFile.getLines().length) {
      return "ERROR, no line " + lineNumber + " in " + testFile.getName();
    } else if (lineNumber == 0) {
      return "<issue on file " + testFile.getName() + ">";
    } else {
      return testFile.lineWithoutNoncompliantComment(lineNumber);
    }
  }

  public List<QuickFix> getQuickfixes(){
    return quickfixes;
  }

  public void setQuickfixes(List<QuickFix> quickfixes){
    this.quickfixes = quickfixes;
  }

}
