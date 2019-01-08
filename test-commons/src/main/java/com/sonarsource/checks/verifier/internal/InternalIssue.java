/*
 * SonarQube Analyzer Test Commons
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

import com.sonarsource.checks.verifier.MultiFileVerifier;
import com.sonarsource.checks.verifier.SingleFileVerifier;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;

class InternalIssue implements SingleFileVerifier.IssueBuilder, MultiFileVerifier.IssueBuilder, SingleFileVerifier.Issue, MultiFileVerifier.Issue {

  private final Path path;
  final String message;
  IssueLocation location;
  List<Secondary> secondaries = new ArrayList<>();
  List<List<Secondary>> flows = new ArrayList<>();
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
