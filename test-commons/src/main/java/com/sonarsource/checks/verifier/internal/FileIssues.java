/*
 * SonarQube Analyzer Test Commons
 * Copyright (C) 2009-2018 SonarSource SA
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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import javax.annotation.Nullable;

public class FileIssues {

  private static final Pattern LINE_NUMBER = Pattern.compile("\n(\\d{3}):");

  public final TestFile testFile;

  private final Map<Integer, LineIssues> expectedIssueMap = new TreeMap<>();

  private final Map<Integer, LineIssues> actualIssueMap = new TreeMap<>();

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
    addActualIssue(line, message, preciseLocation, null);
  }

  /**
   * @param line of the issue, start at 1, same as TokenLocation#startLine()
   */
  public void addActualIssue(int line, String message, @Nullable PrimaryLocation preciseLocation, @Nullable Double effortToFix) {
    LineIssues lineIssues = actualIssueMap.computeIfAbsent(line, key -> LineIssues.at(testFile, line, preciseLocation));
    lineIssues.add(message, effortToFix);
  }

  public Report report() {
    if (!orphanSecondaryOrFlowLocations.isEmpty()) {
      SecondaryLocation orphanSecondary = orphanSecondaryOrFlowLocations.get(0);
      throw new IllegalStateException("Secondary location '>' without next primary location at " + orphanSecondary.range.toString());
    }
    Report report = new Report();

    report.setExpectedIssueCount(expectedIssueMap.values().stream().mapToInt(issues -> issues.messages.size()).sum());

    report.appendExpected(testFile.getName() + "\n" + expectedIssueMap.values().stream()
      .map(LineIssues::validateExpected)
      .map(LineIssues::toString)
      .collect(Collectors.joining("\n")));

    report.setActualIssueCount(actualIssueMap.values().stream().mapToInt(issues -> issues.messages.size()).sum());

    report.appendActual(testFile.getName() + "\n" + actualIssueMap.values().stream()
      .map(lineIssues -> lineIssues.dropUntestedAttributes(expectedIssueMap.get(lineIssues.line)))
      .map(LineIssues::toString)
      .collect(Collectors.joining("\n")));

    int line = firstDiffLine(report.getExpected(), report.getActual());
    report.appendContext("In file (" + testFile.getName() + ":" + line + ")");

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
