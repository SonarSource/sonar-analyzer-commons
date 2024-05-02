/*
 * SonarSource Analyzers Test Commons
 * Copyright (C) 2009-2024 SonarSource SA
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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import org.sonarsource.analyzer.commons.checks.verifier.quickfix.QuickFix;
import org.sonarsource.analyzer.commons.checks.verifier.quickfix.TextSpan;

public class FileIssues {

  private static final Pattern LINE_NUMBER = Pattern.compile("\n(\\d{3}):");

  public final TestFile testFile;

  private final Map<Integer, LineIssues> expectedIssueMap = new TreeMap<>();

  private final Map<Integer, LineIssues> actualIssueMap = new TreeMap<>();

  private final Map<TextSpan, List<QuickFix>> expectedQuickFixes;

  private final Map<Integer, List<QuickFix>> actualQuickFixes = new TreeMap<>();

  @Nullable
  private PrimaryLocation currentPrimary = null;

  private final List<SecondaryLocation> orphanSecondaryOrFlowLocations = new ArrayList<>();

  public FileIssues(TestFile testFile, List<Comment> comments) {
    this.testFile = testFile;
    for (Comment comment : comments) {
      LineIssues lineIssues = NoncompliantCommentParser.parse(testFile, comment.line, comment.content);
      if (lineIssues != null) {
        testFile.addNoncompliantComment(comment);
        LineIssues existingLineIssues = expectedIssueMap.get(lineIssues.line);
        if (existingLineIssues != null) {
          existingLineIssues.merge(lineIssues);
        } else {
          expectedIssueMap.put(lineIssues.line, lineIssues);
        }
      } else {
        List<PreciseLocation> locations = PreciseLocationParser.parse(comment.line, comment.contentColumn, comment.content);
        if (!locations.isEmpty()) {
          testFile.addNoncompliantComment(comment);
          locations.forEach(this::addLocation);
        }
      }
    }

    // Build expected quickfixes map
    var qfv = new QuickFixParser(comments, expectedIssueMap);
    expectedQuickFixes = qfv.expectedQuickFixes;
  }

  private void addLocation(PreciseLocation location) {
    if (location instanceof PrimaryLocation) {
      addPrimary((PrimaryLocation) location);
    } else {
      addSecondary((SecondaryLocation) location);
    }
  }

  private void addPrimary(PrimaryLocation primary) {
    LineIssues lineIssues = expectedIssueMap.get(primary.range.line);
    if (lineIssues == null) {
      throw new IllegalStateException("Primary location does not have a related issue at " + primary.range.toString());
    }
    if (lineIssues.primaryLocation != null) {
      throw new IllegalStateException("Primary location conflicts with another primary location at " + primary.range.toString());
    }
    orphanSecondaryOrFlowLocations.forEach(secondary -> addSecondaryTo(secondary, primary));
    orphanSecondaryOrFlowLocations.clear();
    lineIssues.primaryLocation = primary;
    currentPrimary = primary;
  }

  private void addSecondary(SecondaryLocation secondary) {
    if (secondary.primaryIsBefore) {
      if (currentPrimary == null) {
        throw new IllegalStateException("Secondary location '<' without previous primary location at " + secondary.range.toString());
      }
      addSecondaryTo(secondary, currentPrimary);
    } else {
      orphanSecondaryOrFlowLocations.add(secondary);
    }
  }

  private static void addSecondaryTo(SecondaryLocation secondary, PrimaryLocation primary) {
    if (secondary instanceof FlowLocation) {
      FlowLocation flow = (FlowLocation) secondary;
      for (int flowId = primary.flowLocations.size(); flowId <= flow.flowIndex; flowId++) {
        primary.flowLocations.add(new ArrayList<>());
      }
      List<FlowLocation> flowList = primary.flowLocations.get(flow.flowIndex);
      for (int indexInTheFlow = flowList.size(); indexInTheFlow < flow.indexInTheFlow; indexInTheFlow++) {
        flowList.add(null);
      }
      flowList.set(flow.indexInTheFlow - 1, flow);
    } else {
      primary.secondaryLocations.add(secondary);
    }
  }

  /**
   * @param line of the issue, start at 1, same as TokenLocation#startLine()
   */
  public void addActualIssue(int line, String message, @Nullable PrimaryLocation preciseLocation) {
    addActualIssue(line, message, preciseLocation, null, List.of());
  }

  /**
   * @param line of the issue, start at 1, same as TokenLocation#startLine()
   */
  public void addActualIssue(int line, String message, @Nullable PrimaryLocation preciseLocation, @Nullable Double effortToFix) {
    addActualIssue(line, message, preciseLocation, effortToFix, List.of());
  }

  public void addActualIssue(int line, String message, @Nullable PrimaryLocation preciseLocation, @Nullable Double effortToFix, List<QuickFix> quickfixes) {
    LineIssues lineIssues = actualIssueMap.computeIfAbsent(line, key -> LineIssues.at(testFile, line, preciseLocation));
    lineIssues.add(message, effortToFix);
    actualQuickFixes.computeIfAbsent(line, key -> new ArrayList<>()).addAll(quickfixes);
  }

  public Report report() {
    if (!orphanSecondaryOrFlowLocations.isEmpty()) {
      SecondaryLocation orphanSecondary = orphanSecondaryOrFlowLocations.get(0);
      throw new IllegalStateException("Secondary location '>' without next primary location at " + orphanSecondary.range.toString());
    }
    Report report = new Report();

    report.setExpectedIssueCount(expectedIssueMap.values().stream().mapToInt(issues -> issues.messages.size()).sum());
    report.setExpectedQuickfixCount(expectedQuickFixes.values().stream().mapToInt(List::size).sum());

    String testFileName = "<" + testFile.getName() + ">";
    report.appendExpected(testFileName + "\n" + expectedIssueMap.values().stream()
      .map(LineIssues::validateExpected)
      .map(LineIssues::toString)
      .collect(Collectors.joining("\n")));

    report.appendExpectedQuickfixes(testFileName + "\n" + expectedQuickFixes.entrySet().stream()
      .map(entry ->
        entry.getValue().stream()
          .map(qf -> String.format("line %d: ", entry.getKey().startLine) + qf.toString())
          .collect(Collectors.joining("\n")))
      .collect(Collectors.joining("\n")));

    report.setActualIssueCount(actualIssueMap.values().stream().mapToInt(issues -> issues.messages.size()).sum());
    report.setActualQuickfixCount(actualQuickFixes.values().stream().mapToInt(List::size).sum());

    report.appendActual(testFileName + "\n" + actualIssueMap.values().stream()
      .map(lineIssues -> lineIssues.dropUntestedAttributes(expectedIssueMap.get(lineIssues.line)))
      .map(LineIssues::toString)
      .collect(Collectors.joining("\n")));

    report.appendActualQuickfixes(testFileName + "\n" + actualQuickFixes.entrySet().stream()
      .map(entry ->
        entry.getValue().stream()
          .map(qf -> String.format("line %d: ", entry.getKey()) + qf.toString())
          .collect(Collectors.joining("\n")))
      .collect(Collectors.joining()));

    int line = firstDiffLine(report.getExpected(), report.getActual());
    String diff = "[----------------------------------------------------------------------]\n" +
      "[ '-' means expected but not raised, '+' means raised but not expected ]\n" +
      ReportDiff.diff(report.getExpected(), report.getActual()) +
      "[----------------------------------------------------------------------]\n";
    report.appendContext("In file (" + testFile.getName() + ":" + line + ")\n" + diff);

    int qfLine = firstDiffLine(report.getExpectedQuickfixes(), report.getActualQuickfixes());
    String qfDiff = "[------------------------Quickfixes Error------------------------------]\n" +
      "[ '-' means expected but not provided, '+' means provided but not expected ]\n" +
      ReportDiff.diff(report.getExpectedQuickfixes(), report.getActualQuickfixes()) +
      "[----------------------------------------------------------------------]\n";
    report.appendQuickfixContext("In file (" + testFile.getName() + ":" + qfLine + ")\n" + qfDiff);

    return report;
  }

  private static int firstDiffLine(String expected, String actual) {
    int offset = 0;
    while (offset < expected.length() && offset < actual.length() && expected.charAt(offset) == actual.charAt(offset)) {
      offset++;
    }
    int line = 1;
    Matcher matcher = LINE_NUMBER.matcher(expected);
    while (matcher.find() && matcher.start() <= offset) {
      line = Integer.parseInt(matcher.group(1));
    }
    return line;
  }

}
