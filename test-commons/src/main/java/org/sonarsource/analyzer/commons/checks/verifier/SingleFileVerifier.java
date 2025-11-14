/*
 * SonarSource Analyzers Test Commons
 * Copyright (C) 2009-2025 SonarSource Sàrl
 * mailto:info AT sonarsource DOT com
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the Sonar Source-Available License Version 1, as published by SonarSource Sàrl.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the Sonar Source-Available License for more details.
 *
 * You should have received a copy of the Sonar Source-Available License
 * along with this program; if not, see https://sonarsource.com/license/ssal/
 */
package org.sonarsource.analyzer.commons.checks.verifier;

import java.nio.charset.Charset;
import java.nio.file.Path;
import javax.annotation.Nullable;
import org.sonarsource.analyzer.commons.checks.verifier.internal.InternalIssueVerifier;
import org.sonarsource.analyzer.commons.checks.verifier.quickfix.QuickFix;

/**
 * Example:
 * <pre>
 *   Path path = Paths.get("main.js");
 *
 *   SingleFileVerifier verifier = SingleFileVerifier.create(path, UTF_8);
 *
 *   // use an AST visitor to add all the comment
 *   verifier.addComment(12, 20, " Noncompliant {{Rule message}}", 2, 0);
 *
 *   // report all issues raised by one rule
 *   verifier.reportIssue("Issue on file").onFile();
 *
 *   verifier.reportIssue("Issue on line").onLine(4);
 *
 *   verifier.reportIssue("Issue on range").onRange(9, 11, 9, 13)
 *     .addSecondary(6, 9, 6, 11, "msg");
 *
 *   verifier.assertOneOrMoreIssues();
 * </pre>
 * Example 2:
 * <pre>
 *   // to expect to issue, use:
 *   verifier.assertNoIssues();
 * </pre>
 */
public interface SingleFileVerifier {

  /**
   * @param sourceFilePath
   * @param encoding encoding used to load source file
   */
  static SingleFileVerifier create(Path sourceFilePath, Charset encoding) {
    return new InternalIssueVerifier(sourceFilePath, encoding);
  }

  /**
   * Should be called for all comment of the analyzed source file. Example:
   * <pre>
   *  void visitComment(CommentToken token) {
   *    verifier.addComment(token.line(), token.column(), token.text(), COMMENT_PREFIX_LENGTH, COMMENT_SUFFIX_LENGTH);
   *  }
   * </pre>
   * @param line start at 1, beginning of the comment
   * @param column start at 1, beginning of the comment prefix
   * @param content content of the comment with prefix and suffix
   * @param prefixLength for example, if the prefix is '//' then the length is 2
   * @param suffixLength for example, if the suffix is '--&gt;' then the length is 3
   */
  SingleFileVerifier addComment(int line, int column, String content, int prefixLength, int suffixLength);

  /**
   * Each issue raised by a rule should be reported using this method.
   * <pre>
   *   verifier.reportIssue("Issue on file").onFile();
   *
   *   verifier.reportIssue("Issue on line").onLine(line);
   *
   *   verifier.reportIssue("Issue on range with a secondary location").onRange(line, column, endLine, endColumn)
   *     .addSecondary(secondary.line, secondary.column, secondary.endLine, secondary.endColumn, "Secondary message");
   * </pre>
   * @param message issue message
   */
  IssueBuilder reportIssue(String message);

  /**
   * Run the comparison and expect to find at least one issue.
   */
  void assertOneOrMoreIssues();

  /**
   * Run the comparison and expect to find no issues and no Noncompliant comments in the file.
   */
  void assertNoIssues();

  /**
   * Run the comparison and expect to find no issues. Allows Noncompliant comments to be present in the file.
   */
  void assertNoIssuesRaised();

  /**
   * Sets the verifier to ignore expected quick fixes.
   */
  SingleFileVerifier withoutQuickFixes();

  /**
   * Must always call one and only one of: onFile, onLine, onRange
   */
  interface IssueBuilder {

    /**
     * issue is global to the source code file (not at a specific line number)
     */
    Issue onFile();

    /**
     * issue is related to a specific line number
     * @param line, start at 1
     */
    Issue onLine(int line);

    /**
     * issue is at a precise issue location
     * @param line, start at 1, line number of the first character
     * @param column, start at 1, column number of the first character
     * @param endLine, start at 1, line number of the last character, if the issue is on one line then endLine == line
     * @param endColumn, start at 1, column number of the last character, if there's only one character then endColumn == column
     */
    Issue onRange(int line, int column, int endLine, int endColumn);

  }

  interface Issue {

    /**
     * @param gap Gap used for the computation of the effort (previously effortToFix)
     */
    Issue withGap(@Nullable Double gap);

    /**
     * Add a secondary location with an optional message
     * @param line, start at 1, line number of the first character
     * @param column, start at 1, column number of the first character
     * @param endLine, start at 1, line number of the last character, if the location is on one line then endLine == line
     * @param endColumn, start at 1, column number of the last character, if there's only one character then endColumn == column
     * @param message optional message, can be null
     */
    Issue addSecondary(int line, int column, int endLine, int endColumn, @Nullable String message);

    Issue addQuickFix(QuickFix quickFix);

  }

}
