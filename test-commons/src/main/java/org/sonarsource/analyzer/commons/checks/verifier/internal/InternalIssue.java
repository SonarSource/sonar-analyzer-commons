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
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import org.sonarsource.analyzer.commons.checks.verifier.MultiFileVerifier;
import org.sonarsource.analyzer.commons.checks.verifier.SingleFileVerifier;
import org.sonarsource.analyzer.commons.checks.verifier.quickfix.QuickFix;

class InternalIssue implements SingleFileVerifier.IssueBuilder, MultiFileVerifier.IssueBuilder, SingleFileVerifier.Issue, MultiFileVerifier.Issue {

  private final Path path;
  final String message;
  IssueLocation location;
  List<Secondary> secondaries = new ArrayList<>();
  List<List<Secondary>> flows = new ArrayList<>();
  List<QuickFix> quickFixes = new ArrayList<>();
  @Nullable
  Double gap = null;

  public InternalIssue(Path path, String message) {
    this.path = path;
    this.message = message;
  }

  @Override
  public InternalIssue onFile() {
    this.location = new IssueLocation.File(path);
    return this;
  }

  @Override
  public InternalIssue onLine(int line) {
    this.location = new IssueLocation.Line(path, line);
    return this;
  }

  @Override
  public InternalIssue onRange(int line, int column, int endLine, int endColumn) {
    this.location = new IssueLocation.Range(path, line, column, endLine, endColumn);
    return this;
  }

  @Override
  public InternalIssue withGap(@Nullable Double gap) {
    this.gap = gap;
    return this;
  }

  @Override
  public InternalIssue addSecondary(int line, int column, int endLine, int endColumn, @Nullable String message) {
    return addSecondary(path, line, column, endLine, endColumn, message);
  }

  @Override
  public InternalIssue addQuickFix(QuickFix quickFix) {
    this.quickFixes.add(quickFix);
    return this;
  }

  @Override
  public InternalIssue addSecondary(Path secondaryPath, int line, int column, int endLine, int endColumn, @Nullable String message) {
    secondaries.add(new Secondary(new IssueLocation.Range(secondaryPath, line, column, endLine, endColumn), message));
    return this;
  }

  public InternalIssue addFlow(int line, int column, int endLine, int endColumn, int flowIndex, @Nullable String message) {
    return addFlow(path, line, column, endLine, endColumn, flowIndex, message);
  }

  public InternalIssue addFlow(Path flowPath, int line, int column, int endLine, int endColumn, int flowIndex, @Nullable String message) {
    int flowOffset = flowIndex - 1;
    while (flowOffset >= flows.size()) {
      flows.add(new ArrayList<>());
    }
    flows.get(flowOffset).add(new Secondary(new IssueLocation.Range(flowPath, line, column, endLine, endColumn), message));
    return this;
  }

  public static class Secondary {
    public final IssueLocation.Range range;
    @Nullable
    public final String message;

    public Secondary(IssueLocation.Range range, @Nullable String message) {
      this.range = range;
      this.message = message;
    }
  }
}
