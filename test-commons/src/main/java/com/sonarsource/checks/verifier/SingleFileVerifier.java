/*
 * SonarSource Analyzers Test Commons
 * Copyright (C) 2009-2021 SonarSource SA
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

import com.sonarsource.checks.verifier.internal.InternalIssueVerifier;
import java.nio.charset.Charset;
import java.nio.file.Path;
import javax.annotation.Nullable;

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
   * Run the comparison and expect to find no issue.
   */
  void assertNoIssues();

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
  }

}
