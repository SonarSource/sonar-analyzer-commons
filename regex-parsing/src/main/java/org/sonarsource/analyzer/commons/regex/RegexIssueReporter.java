/*
 * SonarSource Analyzers Regex Parsing Commons
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
