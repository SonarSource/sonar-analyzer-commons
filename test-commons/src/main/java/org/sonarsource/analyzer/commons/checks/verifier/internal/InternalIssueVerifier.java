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

import java.nio.charset.Charset;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.sonarsource.analyzer.commons.checks.verifier.FileContent;
import org.sonarsource.analyzer.commons.checks.verifier.MultiFileVerifier;
import org.sonarsource.analyzer.commons.checks.verifier.SingleFileVerifier;

import static org.junit.Assert.assertEquals;

public class InternalIssueVerifier implements MultiFileVerifier, SingleFileVerifier {

  private final Path mainSourceFilePath;
  private Set<Path> filesToVerify = new LinkedHashSet<>();
  private Map<Path, List<Comment>> comments = new HashMap<>();
  private Map<Path, List<InternalIssue>> actualIssues = new HashMap<>();
  private Charset encoding;
  private boolean verifyQuickFixes = true;

  public InternalIssueVerifier(Path mainSourceFilePath, Charset encoding) {
    this.mainSourceFilePath = mainSourceFilePath.toAbsolutePath();
    this.encoding = encoding;
    filesToVerify.add(this.mainSourceFilePath);
  }

  void addComments(List<Comment> commentList) {
    for (Comment comment : commentList) {
      filesToVerify.add(comment.path);
      comments.computeIfAbsent(comment.path, key -> new ArrayList<>())
        .add(comment);
    }
  }

  @Override
  public InternalIssueVerifier addComment(Path path, int line, int column, String content, int prefixLength, int suffixLength) {
    Path absolutePath = path.toAbsolutePath();
    filesToVerify.add(absolutePath);
    int contentColumn = column + prefixLength;
    String commentContent = content.substring(prefixLength, content.length() - suffixLength);
    comments.computeIfAbsent(absolutePath, key -> new ArrayList<>())
      .add(new Comment(absolutePath, line, column, contentColumn, commentContent));
    return this;
  }

  @Override
  public InternalIssueVerifier addComment(int line, int column, String content, int prefixLength, int suffixLength) {
    return addComment(mainSourceFilePath, line, column, content, prefixLength, suffixLength);
  }

  @Override
  public InternalIssueVerifier withoutQuickFixes() {
    verifyQuickFixes = false;
    return this;
  }

  @Override
  public InternalIssue reportIssue(Path path, String message) {
    Path absolutePath = path.toAbsolutePath();
    filesToVerify.add(absolutePath);
    InternalIssue issue = new InternalIssue(absolutePath, message);
    actualIssues.computeIfAbsent(absolutePath, key -> new ArrayList<>()).add(issue);
    return issue;
  }

  @Override
  public InternalIssue reportIssue(String message) {
    return reportIssue(mainSourceFilePath, message);
  }

  // VisibleForTesting
  Report buildReport() {
    Report report = new Report();
    for (Path path : filesToVerify) {
      TestFile testFile = new TestFile(new FileContent(path, encoding));
      FileIssues fileIssues = new FileIssues(testFile, comments.computeIfAbsent(path, key -> Collections.emptyList()));
      addActualIssues(fileIssues, actualIssues.computeIfAbsent(path, key -> Collections.emptyList()));
      report.append(fileIssues.report());
    }
    return report;
  }

  private static void addActualIssues(FileIssues fileIssues, List<InternalIssue> issues) {
    for (InternalIssue issue : issues) {
      if (issue.location == null) {
        throw new IllegalStateException("Missing location, use 'onFile()', 'onLine(...)', 'onRange(...)'");
      }
      int line;
      PrimaryLocation preciseLocation = null;
      switch (issue.location.getType()) {
        case FILE:
          line = 0;
          break;
        case LINE:
          line = ((IssueLocation.Line) issue.location).getLine();
          break;
        case RANGE:
          IssueLocation.Range range = (IssueLocation.Range) issue.location;
          line = range.getLine();
          preciseLocation = new PrimaryLocation(new UnderlinedRange(range.getLine(), range.getColumn(), range.getEndLine(), range.getEndColumn()), issue.secondaries.size());
          break;
        default:
          throw new IllegalStateException("Unsupported " + issue.location.getType());
      }
      if (preciseLocation != null) {
        for (InternalIssue.Secondary secondary : issue.secondaries) {
          IssueLocation.Range range = secondary.range;
          preciseLocation.addSecondary(new UnderlinedRange(range.getLine(), range.getColumn(), range.getEndLine(), range.getEndColumn()), secondary.message);
        }
      }
      fileIssues.addActualIssue(line, issue.message, preciseLocation, issue.gap, issue.quickFixes);
    }
  }

  @Override
  public void assertOneOrMoreIssues() {
    assertIssues(true);
  }

  @Override
  public void assertNoIssuesRaised() {
    Report report = buildReport();
    assertEquals("ERROR: No issues were expected, but some were found.", 0, report.getActualIssueCount());
  }

  @Override
  public void assertNoIssues() {
    assertIssues(false);
  }

  private void assertIssues(boolean expectsIssues) {
    Report report = buildReport();
    String error = null;
    if (!expectsIssues && report.getExpectedIssueCount() != 0) {
      error = "ERROR: 'assertNoIssues()' is called but there's some 'Noncompliant' comments.";
      report.prependExpected(error + "\n");
    } else if (expectsIssues && report.getExpectedIssueCount() == 0) {
      error = "ERROR: 'assertOneOrMoreIssues()' is called but there's no 'Noncompliant' comments.";
      report.prependExpected(error + "\n");
    } else if (!expectsIssues && report.getActualIssueCount() != 0) {
      error = "ERROR: Found " + report.getActualIssueCount() + " unexpected issues.";
      report.prependActual(error + "\n");
    } else if (expectsIssues && report.getActualIssueCount() == 0) {
      error = "ERROR: Expect some issues, but there's none.";
      report.prependActual(error + "\n");
    } else if (report.getExpectedIssueCount() != report.getActualIssueCount()) {
      error = "ERROR: Expect " + report.getExpectedIssueCount() + " issues instead of " + report.getActualIssueCount() + ".";
    } else if (verifyQuickFixes && report.getExpectedQuickfixCount() <= report.getActualQuickfixCount()) {
      error = "ERROR: Expect " + report.getExpectedQuickfixCount() + " quickfixes instead of " + report.getActualQuickfixCount() + ".";
    }
    if (error != null) {
      report.prependContext(error + " ");
    }
    assertEquals(report.getContext(), report.getExpected(), report.getActual());
    if(verifyQuickFixes && !report.getQuickfixContext().isEmpty()){
      throw new AssertionError(report.getQuickfixContext());
    }
  }

}
