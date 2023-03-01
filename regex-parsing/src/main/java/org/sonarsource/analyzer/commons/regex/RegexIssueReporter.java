/*
 * SonarSource Analyzers Regex Parsing Commons
 * Copyright (C) 2009-2023 SonarSource SA
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
package org.sonarsource.analyzer.commons.regex;

import java.util.List;
import javax.annotation.Nullable;
import org.sonarsource.analyzer.commons.regex.ast.RegexSyntaxElement;

public final class RegexIssueReporter {

  @FunctionalInterface
  public interface ElementIssue {
    void report(RegexSyntaxElement syntaxElement, String message, @Nullable Integer cost, List<RegexIssueLocation> secondaries);
  }

  @FunctionalInterface
  public interface InvocationIssue {
    void report(String message, @Nullable Integer cost, List<RegexIssueLocation> secondaries);
  }
}
